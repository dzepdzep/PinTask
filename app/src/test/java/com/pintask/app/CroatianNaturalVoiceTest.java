package com.pintask.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/**
 * Natural Croatian text recognized by Android's speech service is interpreted
 * without using speech networks or guessing an unclear date/time.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CroatianNaturalVoiceTest {
    private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
    private static final ZonedDateTime AT=ZonedDateTime.of(2026,10,22,18,6,0,0,ZONE);
    private Context context;

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true)
                .commit();
    }

    @Test public void exactReportedSentenceBecomesOneHourTaskWithFiveMinuteEarlyAlarm(){
        String spoken="podsjeti me za sat vremena da ugasim kompjuter podsjeti me bar pet minuta ranije";
        SpokenTask parsed=VoiceLanguages.parse(spoken,AT,"hr-HR");
        assertEquals("ugasim kompjuter",parsed.title);
        assertEquals(AT.plusHours(1).toInstant().toEpochMilli(),parsed.dueAt);
        assertEquals(5,parsed.remindBeforeMinutes);
        assertEquals(0,parsed.repeatCode);
        assertEquals(AT.plusMinutes(55).toInstant().toEpochMilli(),
                parsed.dueAt-parsed.remindBeforeMinutes*60_000L);
        assertEquals(spoken,parsed.original);
    }

    @Test public void variedWordOrderAndNaturalCroatianTimeCases(){
        check("Podsjeti me da ugasim kompjuter za sat vremena pet minuta ranije",
                "ugasim kompjuter",60,5);
        check("Podsjeti me da isključim računalo za sat vremena podsjeti me barem 5 minuta prije",
                "isključim računalo",60,5);
        check("Podsjeti me za dva sata da popijem lijek najmanje deset minuta prije",
                "popijem lijek",120,10);
        check("Podsjeti me za pola sata da zatvorim prozore 5 minuta ranije",
                "zatvorim prozore",30,5);
        check("Podsjeti me za 90 minuta da ugasim kompjuter 15 minuta ranije",
                "ugasim kompjuter",90,15);
        check("podsjeti me za pet minuta da ugasim kompjuter jednu minutu prije",
                "ugasim kompjuter",5,1);
    }

    @Test public void existingEnglishAndGermanRemainUntouched(){
        SpokenTask german=VoiceLanguages.parse("Erinnere mich Müll rausbringen in 30 Minuten",AT,"de-DE");
        assertEquals("Müll rausbringen",german.title);
        assertEquals(AT.plusMinutes(30).toInstant().toEpochMilli(),german.dueAt);
        SpokenTask english=VoiceLanguages.parse("Remind me to switch off computer in 1 hour 5 minutes before",AT,"en-US");
        assertEquals("switch off computer",english.title);
        assertEquals(5,english.remindBeforeMinutes);
    }

    @Test public void refuseInsufficientOrConflictingTiming(){
        assertThrows(IllegalArgumentException.class,()->VoiceLanguages.parse(
                "podsjeti me bar pet minuta ranije da ugasim kompjuter",AT,"hr-HR"));
        assertThrows(IllegalArgumentException.class,()->VoiceLanguages.parse(
                "podsjeti me za pet minuta da ugasim kompjuter pet minuta ranije",AT,"hr-HR"));
        assertThrows(IllegalArgumentException.class,()->VoiceLanguages.parse(
                "podsjeti me za sat vremena da ugasim kompjuter pet minuta ranije deset minuta ranije",
                AT,"hr-HR"));
    }

    @Test public void confirmationKeepsExactCroatianTranscriptAndRealAdvanceMinutes(){
        // UI uses the same manual transcript and review flow as existing voice tasks.
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            VoiceTaskDialog.editCommand(controller.get(),
                    "podsjeti me za sat vremena da ugasim kompjuter podsjeti me bar pet minuta ranije",
                    "hr-HR",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(transcript);
            assertTrue(store.activeAll().isEmpty());
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(transcript,review);
            assertTrue(store.activeAll().isEmpty());
            assertNotNull(findText(review.getWindow().getDecorView(),"ugasim kompjuter"));
            View early=findDescription(review.getWindow().getDecorView(),"Change advance reminder");
            assertNotNull(early);
            assertTrue(((android.widget.Button)early).getText().toString().contains("5 minutes before"));
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activeAll().size());
            TaskStore.Task task=store.activeAll().get(0);
            assertEquals("ugasim kompjuter",task.title);
            assertEquals(5,task.notifyBeforeMinutes);
            assertEquals(5*60_000L,task.dueAt-task.notificationAt());
        }
    }

    private static void check(String speech,String expectedTitle,int hoursOrMinutes,int early) {
        SpokenTask task=VoiceLanguages.parse(speech,AT,"hr-HR");
        assertEquals(speech,expectedTitle,task.title);
        assertEquals(speech,AT.plusMinutes(hoursOrMinutes).toInstant().toEpochMilli(),task.dueAt);
        assertEquals(speech,early,task.remindBeforeMinutes);
    }
    private static View findDescription(View v,String description) {
        if(description.equals(String.valueOf(v.getContentDescription())))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            View child=findDescription(((ViewGroup)v).getChildAt(i),description);
            if(child!=null)return child;
        }
        return null;
    }
    private static TextView findText(View v,String exact) {
        if(v instanceof TextView&&exact.equals(((TextView)v).getText().toString()))return (TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            TextView child=findText(((ViewGroup)v).getChildAt(i),exact);
            if(child!=null)return child;
        }
        return null;
    }
}
