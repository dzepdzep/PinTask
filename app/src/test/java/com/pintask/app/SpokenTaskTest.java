package com.pintask.app;

import android.app.*;
import android.content.*;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SpokenTaskTest {
    private final ZoneId zone=ZoneId.of("Europe/Berlin");
    private final ZonedDateTime now=ZonedDateTime.of(2026,10,20,11,0,0,0,zone);
    private Context context;

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
    }
    @Test public void todayAtThirteenKeepsTaskTitleAndAlarmTime(){
        SpokenTask parsed=SpokenTask.parse("I need to wash the dishes today at 13",now);
        assertEquals("wash the dishes",parsed.title);
        assertEquals(0,parsed.repeatCode);
        assertEquals(ZonedDateTime.of(2026,10,20,13,0,0,0,zone).toInstant().toEpochMilli(),parsed.dueAt);
        assertTrue(parsed.schedule.contains("13:00"));
    }
    @Test public void tomorrowAtTwentyFourIsRejectedRatherThanGuessed(){
        String error=assertThrows(IllegalArgumentException.class,
                ()->SpokenTask.parse("Remind me to wash dishes tomorrow at 24",now)).getMessage();
        assertTrue(error.contains("24:00"));
    }
    @Test public void tomorrowAtMidnightNeedsAnExplicitZeroClock(){
        SpokenTask parsed=SpokenTask.parse("Feed the cat tomorrow at 00:00",now);
        assertEquals(LocalDate.of(2026,10,21),Instant.ofEpochMilli(parsed.dueAt).atZone(zone).toLocalDate());
        assertTrue(parsed.schedule.contains("00:00"));
    }
    @Test public void everyOtherTuesdaySchedulesEveryTwoWeeksFromNextTuesday(){
        SpokenTask parsed=SpokenTask.parse("Take out bins every other Tuesday at 8",now);
        assertEquals("Take out bins",parsed.title);
        assertEquals(TaskStore.REPEAT_FORTNIGHTLY,parsed.repeatCode);
        assertEquals(ZonedDateTime.of(2026,10,27,8,0,0,0,zone).toInstant().toEpochMilli(),parsed.dueAt);
        assertEquals(ZonedDateTime.of(2026,11,10,8,0,0,0,zone).toInstant().toEpochMilli(),
                RepeatRules.next(parsed.dueAt,parsed.repeatCode,parsed.dueAt));
    }
    @Test public void dailyAndSpecificWeekdayRepeatsHaveDistinctCodes(){
        SpokenTask daily=SpokenTask.parse("Clean kitchen every day at 13:30",now);
        assertEquals(TaskStore.REPEAT_DAILY,daily.repeatCode);
        SpokenTask tuesday=SpokenTask.parse("Wash sheets every Tuesday at 09:00",now);
        assertEquals(4000+(1<<1),tuesday.repeatCode);
        assertEquals("Wash sheets",tuesday.title);
    }
    @Test public void hourlyCommandCanStartAfterOneHourOrSpecificDelay(){
        SpokenTask hourly=SpokenTask.parse("Check temperature every hour",now);
        assertEquals(5001,hourly.repeatCode);
        assertEquals(now.plusHours(1).toInstant().toEpochMilli(),hourly.dueAt);
        SpokenTask later=SpokenTask.parse("Refill water in 30 minutes",now);
        assertEquals(0,later.repeatCode);
        assertEquals(now.plusMinutes(30).toInstant().toEpochMilli(),later.dueAt);
        SpokenTask two=SpokenTask.parse("Do the laundry in 2 hours",now);
        assertEquals(now.plusHours(2).toInstant().toEpochMilli(),two.dueAt);
    }
    @Test public void invalidOrPastTimesNeverProduceSilentImmediateReminders(){
        assertThrows(IllegalArgumentException.class,()->SpokenTask.parse("Wash dishes today at 08:00",now));
        assertThrows(IllegalArgumentException.class,()->SpokenTask.parse("Task tomorrow",now));
        assertThrows(IllegalArgumentException.class,()->SpokenTask.parse("Wash dishes every Tuesday",now));
        assertThrows(IllegalArgumentException.class,()->SpokenTask.parse("Wash dishes at 13:99",now));
        assertThrows(IllegalArgumentException.class,()->SpokenTask.parse("Wash dishes in 999 hours",now));
        assertThrows(IllegalArgumentException.class,()->SpokenTask.parse("Wash dishes tomorrow in 30 minutes",now));
    }
    @Test public void unqualifiedTaskCanStillBePinnedImmediately(){
        SpokenTask parsed=SpokenTask.parse("Remind me to buy milk",now);
        assertEquals(0,parsed.dueAt);
        assertEquals(0,parsed.repeatCode);
        assertEquals("buy milk",parsed.title);
    }
    @Test public void microphoneLaunchesSystemRecognizerWithoutSavingAnyTask(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            View root=activity.get().getWindow().getDecorView();
            View mic=findDescription(root,"Speak a task");
            assertNotNull(mic);
            mic.performClick();
            Intent launched=Shadows.shadowOf(activity.get()).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,launched.getAction());
            assertEquals(0,store.activeAll().size());
        }
    }
    @Test public void transcriptHasTwoExplicitConfirmationsBeforeItCreatesTask(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            VoiceTaskDialog.editCommand(activity.get(),"Remind me to buy milk",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(transcript);
            assertEquals(0,store.activeAll().size());
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(0,store.activeAll().size());
            AlertDialog preview=ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(transcript,preview);
            assertNotNull(findText(preview.getWindow().getDecorView(),"Confirm the date, time and repeat schedule before creating this task. If they're incorrect, choose Edit command."));
            preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activeAll().size());
            assertEquals("buy milk",store.activeAll().get(0).title);
        }
    }
    private static View findDescription(View v,String desc) {
        if(desc.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            View child=findDescription(((ViewGroup)v).getChildAt(i),desc);if(child!=null)return child;
        }
        return null;
    }
    private static TextView findText(View v,String exact){
        if(v instanceof TextView&&exact.contentEquals(((TextView)v).getText()))return (TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            TextView item=findText(((ViewGroup)v).getChildAt(i),exact);
            if(item!=null)return item;
        }
        return null;
    }
}
