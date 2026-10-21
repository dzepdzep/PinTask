package com.pintask.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VoiceLanguagesTest {
    private static final ZoneId BERLIN=ZoneId.of("Europe/Berlin");
    private static final ZonedDateTime NOW=ZonedDateTime.of(2026,10,20,11,0,0,0,BERLIN);
    private Context context;
    @Before public void setUp() {
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
        context.deleteDatabase("pintask.db");
    }

    @Test public void germanTodayAtThirteenRecognizesTimeAndPreservesGermanTaskTitle(){
        SpokenTask task=VoiceLanguages.parse(
                "Erinnere mich daran, das Geschirr heute um 13 Uhr zu spülen",NOW,"de-DE");
        assertEquals("das Geschirr zu spülen",task.title);
        assertEquals(NOW.withHour(13).toInstant().toEpochMilli(),task.dueAt);
        assertEquals(0,task.remindBeforeMinutes);
        assertTrue(task.original.contains("Erinnere mich daran"));
    }
    @Test public void germanEveryOtherTuesdayAndSpokenNumberAreUnderstood(){
        SpokenTask task=VoiceLanguages.parse("Müll rausbringen jeden zweiten Dienstag um acht",
                NOW,"de-DE");
        assertEquals("Müll rausbringen",task.title);
        assertEquals(TaskStore.REPEAT_FORTNIGHTLY,task.repeatCode);
        assertEquals(ZonedDateTime.of(2026,10,27,8,0,0,0,BERLIN).toInstant().toEpochMilli(),task.dueAt);
    }
    @Test public void germanAdvanceRemindersAndMinuteDelayWork(){
        SpokenTask early=VoiceLanguages.parse(
                "Erinnere mich daran, Medizin morgen um 14 Uhr 15 Minuten vorher",NOW,"de-DE");
        assertEquals("Medizin",early.title);
        assertEquals(15,early.remindBeforeMinutes);
        assertEquals(ZonedDateTime.of(2026,10,21,14,0,0,0,BERLIN).toInstant().toEpochMilli(),early.dueAt);
        SpokenTask delay=VoiceLanguages.parse("Wasser trinken in fünf Minuten",NOW,"de-DE");
        assertEquals("Wasser trinken",delay.title);
        assertEquals(NOW.plusMinutes(5).toInstant().toEpochMilli(),delay.dueAt);
    }
    @Test public void germanDailyRoutineAndNextWeekday(){
        SpokenTask daily=VoiceLanguages.parse("Medikamente jeden Tag um 13 Uhr",NOW,"de-DE");
        assertEquals(TaskStore.REPEAT_DAILY,daily.repeatCode);
        assertEquals("Müll next Friday at 08:00 rausbringen",VoiceLanguages.normalize("Müll am Freitag um 08:00 rausbringen","de-DE"));
        SpokenTask date=VoiceLanguages.parse("Müll am Freitag um 08:00 rausbringen",NOW,"de-DE");
        assertEquals("Müll rausbringen",date.title);
        assertEquals(ZonedDateTime.of(2026,10,23,8,0,0,0,BERLIN).toInstant().toEpochMilli(),date.dueAt);
    }

    @Test public void croatianTomorrowAtThirteenRecognizesTitleAndDate(){
        SpokenTask task=VoiceLanguages.parse("Podsjeti me da operem suđe sutra u 13 sati",
                NOW,"hr-HR");
        assertEquals("operem suđe",task.title);
        assertEquals(ZonedDateTime.of(2026,10,21,13,0,0,0,BERLIN).toInstant().toEpochMilli(),task.dueAt);
        assertEquals(0,task.repeatCode);
        assertTrue(task.original.startsWith("Podsjeti"));
    }
    @Test public void croatianEveryOtherTuesdayHasCorrectFortnightlySchedule(){
        SpokenTask task=VoiceLanguages.parse("Iznesi smeće svaki drugi utorak u 08:00",NOW,"hr-HR");
        assertEquals("Iznesi smeće",task.title);
        assertEquals(TaskStore.REPEAT_FORTNIGHTLY,task.repeatCode);
        assertEquals(ZonedDateTime.of(2026,10,27,8,0,0,0,BERLIN).toInstant().toEpochMilli(),task.dueAt);
    }
    @Test public void croatianDelayAndEarlyRemindersUseSameAlarmCalculation(){
        SpokenTask delay=VoiceLanguages.parse("Popij lijek za pet minuta",NOW,"hr-HR");
        assertEquals("Popij lijek",delay.title);
        assertEquals(NOW.plusMinutes(5).toInstant().toEpochMilli(),delay.dueAt);
        SpokenTask early=VoiceLanguages.parse("Uzmi lijek sutra u 15 sati 10 minuta prije",NOW,"hr-HR");
        assertEquals("Uzmi lijek",early.title);
        assertEquals(10,early.remindBeforeMinutes);
        assertEquals(ZonedDateTime.of(2026,10,21,15,0,0,0,BERLIN).toInstant().toEpochMilli(),early.dueAt);
        VoiceReviewModel review=new VoiceReviewModel(early,BERLIN);
        assertEquals("10 minutes before",review.advanceLabel());
        assertTrue(review.alarmLabel().contains("14:50"));
    }
    @Test public void croatianDailyAndHourlyRoutinesAreDistinct(){
        SpokenTask daily=VoiceLanguages.parse("Operi suđe svaki dan u 13 sati",NOW,"hr-HR");
        assertEquals(TaskStore.REPEAT_DAILY,daily.repeatCode);
        SpokenTask hourly=VoiceLanguages.parse("Provjeri šećer svaka dva sata",NOW,"hr-HR");
        assertEquals(5002,hourly.repeatCode);
        assertEquals(NOW.plusHours(2).toInstant().toEpochMilli(),hourly.dueAt);
    }

    @Test public void ambiguous24AndPastTimeCannotCreateAFalseReminder(){
        assertThrows(IllegalArgumentException.class,()->
                VoiceLanguages.parse("Uzmi lijek sutra u 24 sati",NOW,"hr-HR"));
        assertThrows(IllegalArgumentException.class,()->
                VoiceLanguages.parse("Medizin heute um 08 Uhr",NOW,"de-DE"));
        assertThrows(IllegalArgumentException.class,()->
                VoiceLanguages.parse("Popij lijek 15 minuta prije",NOW,"hr-HR"));
    }
    @Test public void languagePreferencePersistsAndRecognizerUsesSelectedLocale(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup()){
            assertEquals("en-US",VoiceLanguages.language(activity.get()));
            View microphone=findDescription(activity.get().getWindow().getDecorView(),"Speak a task");
            assertNotNull(microphone);
            assertEquals("🎤 EN",((android.widget.Button)microphone).getText().toString());
            microphone.performClick();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertEquals("No recording before selecting a language",null,
                    Shadows.shadowOf(activity.get()).getNextStartedActivity());
            View croatian=findDescription(dialog.getWindow().getDecorView(),"Speak in Hrvatski");
            assertNotNull(croatian);
            croatian.performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("hr-HR",VoiceLanguages.language(activity.get()));
            View newMicrophone=findDescription(activity.get().getWindow().getDecorView(),"Speak a task");
            assertEquals("🎤 HR",((android.widget.Button)newMicrophone).getText().toString());
            Intent recognizer=Shadows.shadowOf(activity.get()).getNextStartedActivity();
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,recognizer.getAction());
            assertEquals("hr-HR",recognizer.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
            assertEquals("hr-HR",recognizer.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE));
        }
    }

    @Test public void microphoneCanSwitchAllThreeLanguagesWithOneTapEachTime(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            for(int choice:new int[]{1,2,0}){
                View microphone=findDescription(activity.get().getWindow().getDecorView(),"Speak a task");
                assertNotNull(microphone);
                microphone.performClick();
                AlertDialog picker=ShadowAlertDialog.getLatestAlertDialog();
                assertNotNull(picker);
                assertEquals("Picking a language never saves a task",0,store.activeAll().size());
                View language=findDescription(picker.getWindow().getDecorView(),
                        "Speak in "+VoiceLanguages.NAMES[choice]);
                assertNotNull(language);
                language.performClick();
                assertEquals(VoiceLanguages.TAGS[choice],VoiceLanguages.language(activity.get()));
                Intent started=Shadows.shadowOf(activity.get()).getNextStartedActivity();
                assertNotNull(started);
                assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,started.getAction());
                assertEquals(VoiceLanguages.TAGS[choice],
                        started.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
                View updated=findDescription(activity.get().getWindow().getDecorView(),"Speak a task");
                assertEquals("🎤 "+VoiceLanguages.SHORT[choice],
                        ((android.widget.Button)updated).getText().toString());
            }
            assertEquals(0,store.activeAll().size());
        }
    }

    @Test public void cancelLanguagePickerStartsNoRecording(){
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup()){
            int previous=VoiceLanguages.selection(activity.get());
            View microphone=findDescription(activity.get().getWindow().getDecorView(),"Speak a task");
            microphone.performClick();
            AlertDialog picker=ShadowAlertDialog.getLatestAlertDialog();
            picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertNull(Shadows.shadowOf(activity.get()).getNextStartedActivity());
            assertEquals(previous,VoiceLanguages.selection(activity.get()));
        }
    }

    private static View findText(View view,String value) {
        if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=findText(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
    private static View findDescription(View view,String value) {
        if(value.equals(view.getContentDescription()==null?"":view.getContentDescription().toString()))
            return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=findDescription(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
}
