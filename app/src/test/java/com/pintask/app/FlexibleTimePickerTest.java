package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FlexibleTimePickerTest {
    @Test public void clockAllowsMidnightAndLastMinuteOfDay(){
        assertEquals(0,FlexibleTimePicker.parseClock("00:00"));
        assertEquals(210,FlexibleTimePicker.parseClock("03:30"));
        assertEquals(945,FlexibleTimePicker.parseClock("15:45"));
        assertEquals(1439,FlexibleTimePicker.parseClock("23:59"));
    }

    @Test public void clockRejectsInvalidAndAmbiguousTimes(){
        for(String bad:new String[]{"24:00","24:30","23:60","3","3 PM","03:5","","abc","-1:00","00:00:00"}) {
            assertThrows("Must reject "+bad,IllegalArgumentException.class,
                    ()->FlexibleTimePicker.parseClock(bad));
        }
    }

    @Test public void earlyReminderUnderstandsMinutesHoursAndStopwatchFormat(){
        assertEquals(0,FlexibleTimePicker.parseAdvance("0"));
        assertEquals(0,FlexibleTimePicker.parseAdvance("0:00"));
        assertEquals(15,FlexibleTimePicker.parseAdvance("15"));
        assertEquals(15,FlexibleTimePicker.parseAdvance("0:15"));
        assertEquals(77,FlexibleTimePicker.parseAdvance("1:17"));
        assertEquals(90,FlexibleTimePicker.parseAdvance("1h 30m"));
        assertEquals(75,FlexibleTimePicker.parseAdvance("75 minutes"));
        assertEquals(1440,FlexibleTimePicker.parseAdvance("24:00"));
        assertEquals(1440,FlexibleTimePicker.parseAdvance("24 hours"));
    }

    @Test public void earlyReminderRejectsOutsideDbSupportedRange(){
        for(String bad:new String[]{"24:01","25:00","1450","99:00","1:60","-1","","one hour"}) {
            assertThrows("Must reject "+bad,IllegalArgumentException.class,
                    ()->FlexibleTimePicker.parseAdvance(bad));
        }
        assertThrows(IllegalArgumentException.class,()->FlexibleTimePicker.checkedAdvance(-2));
        assertThrows(IllegalArgumentException.class,()->FlexibleTimePicker.checkedAdvance(1441));
    }

    @Test public void labelsRenderExactNonPresetValues(){
        assertEquals("At task time",FlexibleTimePicker.advanceLabel(0));
        assertEquals("1 minute before",FlexibleTimePicker.advanceLabel(1));
        assertEquals("17 minutes before",FlexibleTimePicker.advanceLabel(17));
        assertEquals("1h 17m before",FlexibleTimePicker.advanceLabel(77));
        assertEquals("1 hour before",FlexibleTimePicker.advanceLabel(60));
    }

    @Test public void typingAnEarlyReminderNeedsExplicitConfirmation(){
        try(var activity=Robolectric.buildActivity(Activity.class).setup()){
            AtomicInteger saved=new AtomicInteger(-1);
            FlexibleTimePicker.advance(activity.get(),15,saved::set);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertEquals(-1,saved.get());
            RadioButton type=findRadio(dialog.getWindow().getDecorView(),"Type");
            assertNotNull(type);
            type.setChecked(true);
            EditText input=findInput(dialog.getWindow().getDecorView(),"Type minutes or hours before task");
            assertNotNull(input);
            input.setText("1:17");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(77,saved.get());
        }
    }

    @Test public void invalidTypedInputLeavesDialogOpenWithoutSaving(){
        try(var activity=Robolectric.buildActivity(Activity.class).setup()){
            AtomicInteger saved=new AtomicInteger(-1);
            FlexibleTimePicker.clock(activity.get(),210,saved::set);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            RadioButton type=findRadio(dialog.getWindow().getDecorView(),"Type");
            assertNotNull(type);
            type.setChecked(true);
            EditText input=findInput(dialog.getWindow().getDecorView(),"Type 24-hour clock time");
            assertNotNull(input);
            input.setText("24:30");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(-1,saved.get());
            assertTrue(dialog.isShowing());
            input.setText("03:30");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(210,saved.get());
        }
    }

    private static RadioButton findRadio(View v,String text){
        if(v instanceof RadioButton && text.equals(((RadioButton)v).getText().toString()))return (RadioButton)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            RadioButton hit=findRadio(((ViewGroup)v).getChildAt(i),text);
            if(hit!=null)return hit;
        }
        return null;
    }
    private static EditText findInput(View v,String description){
        if(v instanceof EditText && description.equals(String.valueOf(v.getContentDescription())))return (EditText)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            EditText hit=findInput(((ViewGroup)v).getChildAt(i),description);
            if(hit!=null)return hit;
        }
        return null;
    }
}
