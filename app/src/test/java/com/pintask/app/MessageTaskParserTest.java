package com.pintask.app;

import android.app.*;
import android.content.*;
import android.view.View;
import android.widget.EditText;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Incoming messages and dates must never cause silent, unreviewed tasks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MessageTaskParserTest {
    private static final ZoneId BERLIN=ZoneId.of("Europe/Berlin");
    private static final ZonedDateTime NOW=ZonedDateTime.of(2026,10,21,21,0,0,0,BERLIN);
    private Context context;
    private java.util.TimeZone previousZone;

    @Before public void before(){
        previousZone=java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"));
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
    }

    @After public void after(){java.util.TimeZone.setDefault(previousZone);}

    @Test public void userExampleRecognizesFridayAt330InGermanSummerTime(){
        String incoming="### Friday, 23 October 2026\n\n### 03:30 AM\n\nYour local time in Germany (CESt)";
        SpokenTask result=MessageTaskParser.parse(incoming,NOW);
        assertEquals("",result.title);
        assertEquals(0,result.repeatCode);
        assertEquals(0,result.remindBeforeMinutes);
        assertEquals(ZonedDateTime.of(2026,10,23,3,30,0,0,BERLIN).toInstant().toEpochMilli(),result.dueAt);
        assertEquals(incoming,result.original);
    }

    @Test public void extractsNameWhenMessageHasActualTitle(){
        SpokenTask task=MessageTaskParser.parse(
                "Pick up prescription\nFriday, 23 October 2026\n03:30 AM\nCEST",NOW);
        assertEquals("Pick up prescription",task.title);
    }

    @Test public void acceptsNumericAndIsoDatesAnd24HourTime(){
        assertEquals(LocalDate.of(2026,10,23),
                Instant.ofEpochMilli(MessageTaskParser.parse("23.10.2026\n03:30",NOW).dueAt)
                        .atZone(BERLIN).toLocalDate());
        assertEquals(LocalTime.of(15,30),
                Instant.ofEpochMilli(MessageTaskParser.parse("2026-10-23 at 15:30",NOW).dueAt)
                        .atZone(BERLIN).toLocalTime());
    }

    @Test public void rejectsMismatchedWeekdayAndTimezoneLabels(){
        IllegalArgumentException day=assertThrows(IllegalArgumentException.class,
                ()->MessageTaskParser.parse("Thursday, 23 October 2026\n03:30",NOW));
        assertTrue(day.getMessage().contains("weekday"));
        IllegalArgumentException tz=assertThrows(IllegalArgumentException.class,
                ()->MessageTaskParser.parse("Friday, 23 October 2026 03:30 CET",NOW));
        assertTrue(tz.getMessage().contains("doesn't match"));
    }

    @Test public void rejectsMultipleDifferentDatesOrTimes(){
        assertThrows(IllegalArgumentException.class,()->MessageTaskParser.parse(
                "Friday 23 October 2026 03:30, Saturday 24 October 2026 04:30",NOW));
        assertThrows(IllegalArgumentException.class,()->MessageTaskParser.parse(
                "Friday 23 October 2026 03:30 / 04:30",NOW));
    }

    @Test public void rejectsMissingOrAmbiguousDstDateTime(){
        assertThrows(IllegalArgumentException.class,()->MessageTaskParser.parse("25 October 2026 at 02:30",NOW));
        assertThrows(IllegalArgumentException.class,()->MessageTaskParser.parse("29 March 2026 at 02:30",NOW));
    }

    @Test public void sharedMessageNeverSavesBeforeReviewAndNameConfirmation(){
        String incoming="### Friday, 23 October 2026\n### 03:30 AM\nYour local time in Germany (CESt)";
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)){
            Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT,incoming);
            activity.get().onNewIntent(share);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog first=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(first);
            assertEquals(0,store.activeAll().size());
            first.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(first,review);
            assertNotNull(review);
            assertEquals(0,store.activeAll().size());
            View date=findDescription(review.getWindow().getDecorView(),"Change reminder date");
            View time=findDescription(review.getWindow().getDecorView(),"Change reminder time");
            assertNotNull(date);
            assertNotNull(time);
            assertTrue(((android.widget.Button)date).getText().toString().contains("23 Oct"));
            assertTrue(((android.widget.Button)time).getText().toString().contains("03:30"));
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("Blank title must never auto-save",0,store.activeAll().size());
        }
    }

    private static View findDescription(View view,String text){
        if(text.equals(String.valueOf(view.getContentDescription())))return view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View found=findDescription(group.getChildAt(i),text);
                if(found!=null)return found;
            }
        }
        return null;
    }
}
