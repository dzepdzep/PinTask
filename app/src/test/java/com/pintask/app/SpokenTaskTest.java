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
    @Test public void voiceUnderstandsSpokenAdvanceWarningAndSeparatesDueAndAlarmTime(){
        SpokenTask parsed=SpokenTask.parse(
                "Remind me to wash the dishes tomorrow at 13:00 15 minutes before",now);
        assertEquals("wash the dishes",parsed.title);
        assertEquals(15,parsed.remindBeforeMinutes);
        assertEquals(ZonedDateTime.of(2026,10,21,13,0,0,0,zone).toInstant().toEpochMilli(),
                parsed.dueAt);
        assertEquals(ZonedDateTime.of(2026,10,21,12,45,0,0,zone).toInstant().toEpochMilli(),
                parsed.dueAt-parsed.remindBeforeMinutes*60_000L);
    }
    @Test public void remindersCanBeSpokenBeforeTheTaskNameOrAfterTime(){
        SpokenTask ahead=SpokenTask.parse(
                "Remind me 10 minutes before my appointment tomorrow at 14:00",now);
        assertEquals("my appointment",ahead.title);
        assertEquals(10,ahead.remindBeforeMinutes);
        SpokenTask after=SpokenTask.parse(
                "Take medication today at 20:00 5 minutes early",now);
        assertEquals("Take medication",after.title);
        assertEquals(5,after.remindBeforeMinutes);
        SpokenTask words=SpokenTask.parse(
                "Wash dishes tomorrow at 13:00 fifteen minutes before",now);
        assertEquals(15,words.remindBeforeMinutes);
    }
    @Test public void halfHourAndRepeatPatternCanBeSpokenTogether(){
        SpokenTask parsed=SpokenTask.parse(
                "Remind me every other Tuesday at 08:00, half an hour before, to take out the bins",now);
        assertEquals("take out the bins",parsed.title);
        assertEquals(30,parsed.remindBeforeMinutes);
        assertEquals(TaskStore.REPEAT_FORTNIGHTLY,parsed.repeatCode);
        assertEquals(ZonedDateTime.of(2026,10,27,7,30,0,0,zone).toInstant().toEpochMilli(),
                parsed.dueAt-30*60_000L);
    }
    @Test public void unsafeOrDoubleSpokenAdvanceIsRejected(){
        assertThrows(IllegalArgumentException.class,
                ()->SpokenTask.parse("Remind me to clean dishes today at 11:05 15 minutes before",now));
        assertThrows(IllegalArgumentException.class,
                ()->SpokenTask.parse("Wash dishes tomorrow at 13:00 2000 minutes before",now));
        assertThrows(IllegalArgumentException.class,
                ()->SpokenTask.parse("Wash dishes tomorrow at 13:00 5 minutes before and 10 minutes before",now));
        assertThrows(IllegalArgumentException.class,
                ()->SpokenTask.parse("Wash dishes 10 minutes before",now));
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
            assertNotNull(findText(preview.getWindow().getDecorView(),"WHAT I HEARD"));
            assertNotNull(findDescription(preview.getWindow().getDecorView(),"Change reminder date"));
            assertNotNull(findDescription(preview.getWindow().getDecorView(),"Change reminder time"));
            assertNotNull(findDescription(preview.getWindow().getDecorView(),"Change repeat pattern"));
            preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activeAll().size());
            assertEquals("buy milk",store.activeAll().get(0).title);
        }
    }
    @Test public void separatelyCorrectingTimeActuallyChangesSavedAlarmAndKeepsTranscribedTask(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            VoiceTaskDialog.editCommand(activity.get(),"Wash dishes tomorrow at 13:00",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            View time=findDescription(review.getWindow().getDecorView(),"Change reminder time");
            assertNotNull(time);
            assertTrue(((android.widget.Button)time).getText().toString().contains("13:00"));
            time.performClick();
            android.app.TimePickerDialog picker=
                (android.app.TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            picker.updateTime(14,45);
            picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(((android.widget.Button)time).getText().toString().contains("14:45"));
            assertEquals(0,store.activeAll().size());
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activeAll().size());
            TaskStore.Task saved=store.activeAll().get(0);
            java.time.LocalDate day=java.time.LocalDate.now().plusDays(1);
            long expected=day.atTime(14,45).atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            assertEquals(expected,saved.dueAt);
            assertEquals(0,saved.notifyBeforeMinutes);
            assertEquals("Wash dishes",saved.title);
        }
    }
    @Test public void independentRepeatPickerUpdatesPreviewButSavesOnlyOnConfirmation(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            VoiceTaskDialog.editCommand(activity.get(),"Water flowers tomorrow at 18:00",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            View repeat=findDescription(review.getWindow().getDecorView(),"Change repeat pattern");
            assertNotNull(repeat);
            repeat.performClick();
            AlertDialog options=ShadowAlertDialog.getLatestAlertDialog();
            options.getListView().performItemClick(null,2,2); // Every day
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(((android.widget.Button)repeat).getText().toString().contains("Daily"));
            assertEquals(0,store.activeAll().size());
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activeAll().size());
            TaskStore.Task saved=store.activeAll().get(0);
            assertEquals(TaskStore.REPEAT_DAILY,saved.recurrence);
            assertTrue(saved.autoRepeat);
        }
    }

    @Test public void voiceSpokenEarlyReminderPersistsRealAdvancedAlarm(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            VoiceTaskDialog.editCommand(activity.get(),
                    "Wash dishes tomorrow at 13:00 15 minutes before",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            View early=findDescription(review.getWindow().getDecorView(),"Change advance reminder");
            assertNotNull(early);
            assertTrue(((android.widget.Button)early).getText().toString().contains("15 minutes before"));
            assertEquals(0,store.activeAll().size());
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activeAll().size());
            TaskStore.Task persisted=store.activeAll().get(0);
            assertEquals(15,persisted.notifyBeforeMinutes);
            assertEquals(15*60_000L,persisted.dueAt-persisted.notificationAt());
            assertEquals("Wash dishes",persisted.title);
            assertEquals(0,persisted.recurrence);
        }
    }
    @Test public void manualAdvancePresetOverridesWhatSpeechRecognitionHeard(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            VoiceTaskDialog.editCommand(activity.get(),"Wash dishes tomorrow at 13:00",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog()
                    .getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            View early=findDescription(review.getWindow().getDecorView(),"Change advance reminder");
            assertNotNull(early);
            early.performClick();
            AlertDialog options=ShadowAlertDialog.getLatestAlertDialog();
            options.getListView().performItemClick(null,3,3); // 15 min before
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(((android.widget.Button)early).getText().toString().contains("15 minutes before"));
            assertEquals(0,store.activeAll().size());
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(15,store.activeAll().get(0).notifyBeforeMinutes);
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
