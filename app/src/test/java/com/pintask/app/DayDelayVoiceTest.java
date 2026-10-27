package com.pintask.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Regression: Android often transcribes "in two days" as "into days". */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class DayDelayVoiceTest {
    private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
    // Crossing October's DST boundary verifies calendar days (same local
    // clock time), not a blind +48 hours.
    private static final ZonedDateTime NOW=ZonedDateTime.of(2026,10,24,12,36,0,0,ZONE);
    private Context context;

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
    }

    @Test public void exactRecognizerTranscriptCreatesCorrectTaskAndEarlyReminder(){
        String phrase="take the trash out into days remind me 15 minutes earlier";
        assertDue(phrase,"en-US","take the trash out",NOW.plusDays(2),15);
        // Raw transcription, not a modified 'spoken' field, stays visible in review.
        assertEquals(phrase,VoiceLanguages.parse(phrase,NOW,"en-US").original);
    }

    @Test public void accuratelyRecognizedDayPhrasesAlsoWork(){
        assertDue("Take the trash out in two days. Remind me 15 minutes earlier.",
                "en-US","Take the trash out",NOW.plusDays(2),15);
        assertDue("Remind me to put out the bins in 2 days, 15 minutes early",
                "en-US","put out the bins",NOW.plusDays(2),15);
        assertDue("Remind me in one day to call home 5 minutes beforehand",
                "en-US","call home",NOW.plusDays(1),5);
        assertDue("Pay rent in a week 30 minutes before",
                "en-US","Pay rent",NOW.plusWeeks(1),30);
        assertDue("Pay rent in 4 weeks 30 minutes before",
                "en-US","Pay rent",NOW.plusWeeks(4),30);
    }

    @Test public void germanAndCroatianDayPhrasesWorkWithoutAffectingHourPhrases(){
        assertDue("Erinnere mich in zwei Tagen daran, den Müll rauszubringen. Erinnere mich 15 Minuten vorher.",
                "de-DE","den Müll rauszubringen",NOW.plusDays(2),15);
        assertDue("Erinnere mich in einem Tag daran, die Tür zu prüfen. Erinnere mich fünf Minuten vorher.",
                "de-DE","die Tür zu prüfen",NOW.plusDays(1),5);
        assertDue("Podsjeti me za dva dana da iznesem smeće petnaest minuta ranije",
                "hr-HR","iznesem smeće",NOW.plusDays(2),15);
        assertDue("Podsjeti me za jedan dan da zatvorim prozor pet minuta prije",
                "hr-HR","zatvorim prozor",NOW.plusDays(1),5);
        assertDue("Remind me in an hour to lock the door 5 minutes earlier",
                "en-US","lock the door",NOW.plusHours(1),5);
    }

    @Test public void germanVoiceDayAndWeekExpressionsWithAdvanceWarnings(){
        // The voice service may return spoken number words or digits; both
        // must resolve to the same calendar due date and early-alarm offset.
        assertDue("Erinnere mich in zwei Tagen daran, den Müll rauszubringen, 15 Minuten vorher",
                "de-DE","den Müll rauszubringen",NOW.plusDays(2),15);
        assertDue("Erinnere mich in 2 Tagen daran, den Müll rauszubringen, fünf Minuten früher",
                "de-DE","den Müll rauszubringen",NOW.plusDays(2),5);
        assertDue("Erinnere mich in drei Tagen daran, die Blumen zu gießen. Erinnere mich zehn Minuten vorher.",
                "de-DE","die Blumen zu gießen",NOW.plusDays(3),10);
        assertDue("Erinnere mich in einer Woche daran, den Brief abzuschicken. Erinnere mich 30 Minuten vorher.",
                "de-DE","den Brief abzuschicken",NOW.plusWeeks(1),30);
        assertDue("Erinnere mich in zwei Wochen daran, den Termin zu prüfen. Erinnere mich 15 Minuten vorher.",
                "de-DE","den Termin zu prüfen",NOW.plusWeeks(2),15);
        assertDue("Erinnere mich in einem Tag daran, die Tür zu schließen.",
                "de-DE","die Tür zu schließen",NOW.plusDays(1),0);
    }

    @Test public void germanVoiceRejectsUnsupportedOrConflictingDates(){
        for(String spoken:new String[]{
                "Erinnere mich in 31 Tagen daran, die Rechnung zu zahlen.",
                "Erinnere mich in fünf Wochen daran, die Rechnung zu zahlen.",
                "Erinnere mich in zwei Tagen um 14 Uhr daran, Müll rauszubringen.",
                "Erinnere mich in zwei Tagen daran, Müll rauszubringen. 15 Minuten vorher. 20 Minuten vorher."
        })assertThrows(spoken,IllegalArgumentException.class,
                ()->VoiceLanguages.parse(spoken,NOW,"de-DE"));
    }

    @Test public void invalidOrExcessiveCalendarOffsetsDoNotCreateUnexpectedTasks(){
        for(String bad:new String[]{
                "Remind me to pay rent in 31 days 5 minutes earlier",
                "Remind me to pay rent in 5 weeks 5 minutes earlier",
                "Remind me to go out in 0 days",
                "Remind me to go out in 0.5 days 5 minutes earlier",
                "Remind me to go out in 2 days in 3 hours 5 minutes earlier",
                "Remind me to go out 15 minutes early"
        })assertThrows(bad,IllegalArgumentException.class,
                ()->VoiceLanguages.parse(bad,NOW,"en-US"));
    }

    @Test public void reviewShowsCorrectDateAndEarlyWarningBeforeSaving(){
        // UI always renders time and advance reminder before it creates anything.
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            String speech="take the trash out into days remind me 15 minutes earlier";
            VoiceTaskDialog.editCommand(activity.get(),speech,"en-US",()->{});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(0,store.activeAll().size());
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(transcript,review);
            assertEquals(0,store.activeAll().size());
            EditText title=(EditText)findDescription(review.getWindow().getDecorView(),"Recognized task name");
            assertEquals("take the trash out",title.getText().toString());
            Button early=(Button)findDescription(review.getWindow().getDecorView(),"Change advance reminder");
            assertNotNull(early);
            assertTrue(early.getText().toString().contains("15 minutes before"));
        }
    }

    private static void assertDue(String spoken,String lang,String title,
                                  ZonedDateTime localDue,int advanceMinutes){
        SpokenTask parsed=VoiceLanguages.parse(spoken,NOW,lang);
        assertEquals(spoken,title,parsed.title);
        assertEquals(spoken,localDue.toInstant().toEpochMilli(),parsed.dueAt);
        assertEquals(spoken,advanceMinutes,parsed.remindBeforeMinutes);
        assertEquals(spoken,localDue.minusMinutes(advanceMinutes).toInstant().toEpochMilli(),
                parsed.dueAt-advanceMinutes*60_000L);
        assertEquals(spoken,0,parsed.repeatCode);
    }

    private static View findDescription(View root,String description){
        if(description.equals(String.valueOf(root.getContentDescription())))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View v=findDescription(((ViewGroup)root).getChildAt(i),description);
            if(v!=null)return v;
        }
        return null;
    }
}
