package com.pintask.app;

import android.app.*;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Verify exact interpretations and the editable confirmation/save workflow. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VoiceTaskInputTest {
    Context context;
    private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
    private static final ZonedDateTime NOW=ZonedDateTime.of(2026,10,20,12,0,0,0,ZONE);

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("interactive_shift_guide_v29",true)
                .putBoolean("startup_access_reviewed_v31",true).commit();
    }

    @Test public void parsesTodayThirteenOClock(){
        VoiceTaskParser.Result p=VoiceTaskParser.parse("I need to wash the dishes today at 13",NOW);
        assertEquals("wash the dishes",p.title);
        assertEquals(LocalDate.of(2026,10,20),p.date);
        assertEquals(LocalTime.of(13,0),p.time);
        assertTrue(p.scheduleMentioned);
        assertEquals(0,p.recurrence);
    }

    @Test public void tomorrowAtTwentyFourMeansMidnightTheFollowingDay(){
        VoiceTaskParser.Result p=VoiceTaskParser.parse("Remind me tomorrow at 24",NOW);
        assertEquals(LocalDate.of(2026,10,22),p.date);
        assertEquals(LocalTime.MIDNIGHT,p.time);
        assertTrue(p.note.contains("24:00"));
    }

    @Test public void everyOtherTuesdayWithOrWithoutTime(){
        VoiceTaskParser.Result unspecified=VoiceTaskParser.parse("Take out the bins every other Tuesday",NOW);
        assertEquals("take out the bins",unspecified.title);
        assertEquals(4,unspecified.recurrence);
        assertTrue(unspecified.scheduleMentioned);
        assertNull("Must never guess an unspoken clock time",unspecified.time);
        assertEquals(LocalDate.of(2026,10,27),unspecified.date);

        VoiceTaskParser.Result seven=VoiceTaskParser.parse("Take out the bins every other Tuesday at 7",NOW);
        assertEquals(LocalDate.of(2026,10,27),seven.date); // 07:00 today has passed
        assertEquals(LocalTime.of(7,0),seven.time);
        assertEquals(4,seven.recurrence);
        assertEquals(ZONE,seven.date.atTime(seven.time).atZone(ZONE).getZone());
    }

    @Test public void dailyAndHourlyVoiceSchedules(){
        VoiceTaskParser.Result daily=VoiceTaskParser.parse("Water plants every day at 7",NOW);
        assertEquals(1,daily.recurrence);
        assertEquals(LocalDate.of(2026,10,21),daily.date);
        assertEquals(LocalTime.of(7,0),daily.time);

        VoiceTaskParser.Result hourly=VoiceTaskParser.parse("Check laundry every 2 hours",NOW);
        assertEquals(5002,hourly.recurrence);
        assertEquals(LocalTime.of(14,0),hourly.time);
    }

    @Test public void supportsGermanDatesAndErrorsInsteadOfSilentlyGuessing(){
        VoiceTaskParser.Result german=VoiceTaskParser.parse("Ich muss Küche aufräumen morgen um 13 Uhr",NOW);
        assertEquals("küche aufräumen",german.title);
        assertEquals(LocalDate.of(2026,10,21),german.date);
        assertEquals(LocalTime.of(13,0),german.time);
        VoiceTaskParser.Result undated=VoiceTaskParser.parse("Take bins next week",NOW);
        assertTrue(undated.scheduleMentioned);
        assertNull(undated.time);
        assertFalse(undated.note.isEmpty());
        assertThrows(IllegalArgumentException.class,()->VoiceTaskParser.parse("Today at 25",NOW));
        assertThrows(IllegalArgumentException.class,()->VoiceTaskParser.parse("Tomorrow at 24:30",NOW));
    }

    @Test public void reviewSaveCreatesRealScheduledAlarmWithoutUsingQuickAdd() {
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)) {
            String phrase="Wash dishes tomorrow at 13";
            VoiceTaskEntry.review(controller.get(),phrase);
            AlertDialog preview=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(preview);
            assertEquals("Save task",preview.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            EditText title=firstEditText(preview.getWindow().getDecorView());
            assertNotNull(title);
            title.setText("Wash pots");
            preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1,store.activePersonal().size());
            TaskStore.Task saved=store.activePersonal().get(0);
            assertEquals("Wash pots",saved.title);
            assertEquals(0,saved.notifyBeforeMinutes);
            assertTrue(saved.reminderEnabled());
            assertTrue(saved.dueAt>System.currentTimeMillis());
        }
    }

    private static EditText firstEditText(View root){
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            EditText child=firstEditText(((ViewGroup)root).getChildAt(i));
            if(child!=null)return child;
        }
        return null;
    }
}
