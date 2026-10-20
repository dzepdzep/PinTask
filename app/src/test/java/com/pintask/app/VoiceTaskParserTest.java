package com.pintask.app;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VoiceTaskParserTest {
    private final ZoneId zone=ZoneId.of("Europe/Berlin");
    private ZonedDateTime now(){return ZonedDateTime.of(2026,10,20,12,15,0,0,zone);}

    @Test public void todayAtThirteenPreservesTitleAndTime(){
        VoiceTaskParser.Draft d=VoiceTaskParser.parse("I need to wash the dishes today at 13",now());
        assertEquals("wash the dishes",d.title);
        assertEquals(LocalDate.of(2026,10,20),d.day);
        assertEquals(13,d.hour);assertEquals(0,d.minute);assertEquals(0,d.recurrence);
        assertEquals(LocalDateTime.of(2026,10,20,13,0).atZone(zone).toInstant().toEpochMilli(),d.dueMillis(zone));
    }

    @Test public void tomorrowAtTwentyFourAlwaysAsksForMidnightInterpretation(){
        VoiceTaskParser.Draft d=VoiceTaskParser.parse("wash dishes tomorrow at 24",now());
        assertEquals("wash dishes",d.title);
        assertTrue(d.midnightNeedsConfirmation);
        assertEquals(LocalDate.of(2026,10,21),d.day);
        assertEquals(0,d.hour);assertEquals(0,d.minute);
        // The end-of-tomorrow choice is Oct 22 00:00, never Oct 21 00:00.
        d.day=d.day.plusDays(1);
        assertEquals(LocalDateTime.of(2026,10,22,0,0).atZone(zone).toInstant().toEpochMilli(),d.dueMillis(zone));
    }

    @Test public void everyOtherTuesdayMakesFortnightlyScheduleAndRequestsTime(){
        VoiceTaskParser.Draft d=VoiceTaskParser.parse("Take out bins every other Tuesday",now());
        assertEquals("Take out bins",d.title);
        assertEquals(TaskStore.REPEAT_FORTNIGHTLY,d.recurrence);
        assertEquals(LocalDate.of(2026,10,20),d.day);
        assertFalse(d.hasTime());
        assertTrue(d.warning.contains("Choose a time"));
        assertThrows(IllegalArgumentException.class,()->d.dueMillis(zone));
    }

    @Test public void weeklyTuesdayWithTimeKeepsWeekdayAnchor(){
        VoiceTaskParser.Draft d=VoiceTaskParser.parse("Take out bins every Tuesday at 20:30",now());
        assertEquals(TaskStore.REPEAT_WEEKLY,d.recurrence);
        assertEquals("Take out bins",d.title);
        assertEquals(LocalDate.of(2026,10,20),d.day);
        assertEquals(20,d.hour);assertEquals(30,d.minute);
    }

    @Test public void germanAndCroatianCommonPhrasesHaveNoHiddenCloudParser(){
        VoiceTaskParser.Draft de=VoiceTaskParser.parse("Ich muss Geschirr waschen morgen um 13 Uhr",now());
        assertEquals("Geschirr waschen",de.title);
        assertEquals(LocalDate.of(2026,10,21),de.day);
        assertEquals(13,de.hour);
        VoiceTaskParser.Draft hr=VoiceTaskParser.parse("Moram oprati suđe sutra u 13 sati",now());
        assertEquals("oprati suđe",hr.title);
        assertEquals(LocalDate.of(2026,10,21),hr.day);
        assertEquals(13,hr.hour);
    }

    @Test public void neverSilentlyTreatPastTodayAsTomorrow(){
        VoiceTaskParser.Draft d=VoiceTaskParser.parse("Wash dishes today at 08",now());
        assertEquals(LocalDate.of(2026,10,20),d.day);
        assertTrue(d.dueMillis(zone)<now().toInstant().toEpochMilli());
    }

    @Test public void numbersCannotOverflowClockOrCreateWrongCalendarEvent(){
        assertThrows(IllegalArgumentException.class,()->VoiceTaskParser.parse("Wash dishes tomorrow at 25",now()));
        assertThrows(IllegalArgumentException.class,()->VoiceTaskParser.parse("Wash dishes at 24:30",now()));
        assertThrows(IllegalArgumentException.class,()->VoiceTaskParser.parse("Wash dishes at 13:79",now()));
    }

    @Test public void hourlyVoiceSchedulePreservesNumberAndRequiresNoClock(){
        VoiceTaskParser.Draft d=VoiceTaskParser.parse("Check the laundry every two hours",now());
        assertEquals("Check the laundry",d.title);
        assertEquals(5002,d.recurrence);
        assertEquals(12,d.hour);
        assertEquals(15,d.minute);
    }

    @Test public void pastDailyRepeatMovesToTomorrowButPastOneOffStaysPast(){
        ZonedDateTime n=ZonedDateTime.of(2026,10,20,21,30,0,0,zone);
        VoiceTaskParser.Draft daily=VoiceTaskParser.parse("Wash dishes every day at 13",n);
        assertEquals(LocalDate.of(2026,10,21),daily.day);
        assertEquals(TaskStore.REPEAT_DAILY,daily.recurrence);
        VoiceTaskParser.Draft tuesday=VoiceTaskParser.parse("Bins every other Tuesday at 13",n);
        assertEquals(LocalDate.of(2026,10,27),tuesday.day);
        assertEquals(TaskStore.REPEAT_FORTNIGHTLY,tuesday.recurrence);
        VoiceTaskParser.Draft oneOff=VoiceTaskParser.parse("Wash dishes today at 13",n);
        assertEquals(LocalDate.of(2026,10,20),oneOff.day);
    }

    @Test public void mainTasksShowsAccessibleVoiceIconAndResultOpensEditableReview(){
        Context ctx=RuntimeEnvironment.getApplication();
        ctx.deleteDatabase("pintask.db");
        ctx.getSharedPreferences("settings",0).edit()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            ShiftActivity activity=controller.get();
            assertNotNull(findDescription(activity.getWindow().getDecorView(),"Speak a task"));
            Intent returned=new Intent();
            returned.putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS,
                    new java.util.ArrayList<>(java.util.Arrays.asList("Wash dishes tomorrow at 13")));
            activity.onActivityResult(407,Activity.RESULT_OK,returned);
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(findText(review.getWindow().getDecorView(),
                    "Heard: Wash dishes tomorrow at 13"));
            assertNotNull(findDescription(review.getWindow().getDecorView(),"Choose reminder date"));
            assertNotNull(findDescription(review.getWindow().getDecorView(),"Choose reminder time"));
            assertNotNull(findDescription(review.getWindow().getDecorView(),"Choose recurrence"));
            try(TaskStore store=new TaskStore(activity)){assertEquals(0,store.activeAll().size());}
        }
    }

    @Test public void confirmedVoiceTaskSchedulesExactlyOneTimedAlert(){
        Context ctx=RuntimeEnvironment.getApplication();
        ctx.deleteDatabase("pintask.db");
        ctx.getSharedPreferences("settings",0).edit()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            ShiftActivity activity=controller.get();
            VoiceTaskParser.Draft draft=VoiceTaskParser.parse("Wash dishes tomorrow at 13",ZonedDateTime.now());
            VoiceTaskReview.show(activity,draft,()->{});
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            try(TaskStore store=new TaskStore(activity)){
                assertEquals(1,store.activeAll().size());
                TaskStore.Task task=store.activeAll().get(0);
                assertEquals("Wash dishes",task.title);
                assertEquals(0,task.notifyBeforeMinutes);
                assertEquals(0,task.recurrence);
                assertTrue(task.dueAt>System.currentTimeMillis());
            }
            assertEquals(1,Shadows.shadowOf(activity.getSystemService(AlarmManager.class))
                    .getScheduledAlarms().size());
        }
    }

    private static View findDescription(View root,String text){
        if(text.contentEquals(root.getContentDescription()))return root;
        if(root instanceof ViewGroup){
            for(int i=0;i<((ViewGroup)root).getChildCount();i++){
                View match=findDescription(((ViewGroup)root).getChildAt(i),text);
                if(match!=null)return match;
            }
        }
        return null;
    }

    @Test public void voiceReviewNeverCreatesTaskBeforeConfirmation(){
        Context ctx=RuntimeEnvironment.getApplication();
        ctx.deleteDatabase("pintask.db");
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            ShiftActivity activity=controller.get();
            activity.getSharedPreferences("settings",0).edit()
                    .putBoolean("startup_access_reviewed_v31",true)
                    .putBoolean("interactive_shift_guide_v29",true).commit();
            VoiceTaskParser.Draft parsed=VoiceTaskParser.parse("Wash dishes tomorrow at 13",now());
            VoiceTaskReview.show(activity,parsed,()->{});
            AlertDialog preview=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(findText(preview.getWindow().getDecorView(),"Confirm spoken task"));
            assertNotNull(findText(preview.getWindow().getDecorView(),"Heard: Wash dishes tomorrow at 13"));
            assertNotNull(findText(preview.getWindow().getDecorView(),"Save task"));
            try(TaskStore store=new TaskStore(activity)){
                assertEquals(0,store.activeAll().size());
            }
            preview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            try(TaskStore store=new TaskStore(activity)){
                assertEquals(0,store.activeAll().size());
            }
        }
    }
    private static TextView findText(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return (TextView)v;
        if(v instanceof ViewGroup){
            for(int i=0;i<((ViewGroup)v).getChildCount();i++){
                TextView child=findText(((ViewGroup)v).getChildAt(i),text);
                if(child!=null)return child;
            }
        }
        return null;
    }
}
