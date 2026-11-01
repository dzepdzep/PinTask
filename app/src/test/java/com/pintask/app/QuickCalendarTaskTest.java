package com.pintask.app;

import android.app.*;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class QuickCalendarTaskTest {
    private Context context;
    private TaskStore store;
    @Before public void before() {
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
        context.getSystemService(NotificationManager.class).cancelAll();
        store=new TaskStore(context);
    }
    @After public void after(){store.close();}

    @Test public void tomorrowAsksForTimeAndSchedulesExactLocalAlarm() {
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();
            firstEntry(root).setText("Put bins outside tomorrow");
            findText(root,"When: Now  ▾").performClick();
            AlertDialog chooser=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(findDescription(chooser.getWindow().getDecorView(),
                    "Choose tomorrow and time"));
            findDescription(chooser.getWindow().getDecorView(),
                    "Choose tomorrow and time").performClick();
            TimePickerDialog time=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            time.updateTime(11,25);
            time.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            root=activity.get().getWindow().getDecorView();
            LocalDate tomorrow=LocalDate.now().plusDays(1);
            long expected=QuickDateTimePicker.at(tomorrow,11,25,
                    ZoneId.systemDefault(),System.currentTimeMillis());
            String label=tomorrow.atTime(11,25)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"));
            assertNotNull(findText(root,"When: "+label+"  ▾"));
            assertEquals("Draft must survive picker", "Put bins outside tomorrow",
                    firstEntry(root).getText().toString());
            findText(root,"Add").performClick();
            TaskStore.Task saved=store.activePersonal().get(0);
            assertEquals(expected,saved.dueAt);
            assertEquals(0,saved.notifyBeforeMinutes);
            assertTrue(saved.reminderEnabled());
            assertEquals(1,Shadows.shadowOf(context.getSystemService(AlarmManager.class))
                    .getScheduledAlarms().size());
            assertNotNull(findText(activity.get().getWindow().getDecorView(),"When: Now  ▾"));
        }
    }

    @Test public void pickingCalendarDateAndClockIsNotConvertedToCountdown() {
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();
            firstEntry(root).setText("Doctor's appointment");
            findText(root,"When: Now  ▾").performClick();
            AlertDialog chooser=ShadowAlertDialog.getLatestAlertDialog();
            findDescription(chooser.getWindow().getDecorView(),
                    "Choose calendar date and time").performClick();
            DatePickerDialog picker=(DatePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            LocalDate date=LocalDate.now().plusDays(8);
            picker.getDatePicker().updateDate(date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());
            // Robolectric's DatePickerDialog button can finish without sending
            // OnDateSet; exercise the actual listener deterministically.
            picker.onClick(picker,AlertDialog.BUTTON_POSITIVE);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            TimePickerDialog time=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            time.updateTime(16,45);
            time.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            long expected=QuickDateTimePicker.at(date,16,45,
                    ZoneId.systemDefault(),System.currentTimeMillis());
            root=activity.get().getWindow().getDecorView();
            assertNotNull(findText(root,"When: "+date.atTime(16,45)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))+"  ▾"));
            findText(root,"Add").performClick();
            assertEquals(expected,store.activePersonal().get(0).dueAt);
        }
    }

    @Test public void calendarSelectionCanBeReplacedWithCountdownOrNow() {
        try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();
            findText(root,"When: Now  ▾").performClick();
            findDescription(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),
                    "Choose tomorrow and time").performClick();
            TimePickerDialog time=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            time.updateTime(9,0);
            time.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            root=activity.get().getWindow().getDecorView();
            String tomorrow=LocalDate.now().plusDays(1).atTime(9,0)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"));
            findText(root,"When: "+tomorrow+"  ▾").performClick();
            AlertDialog chooser=ShadowAlertDialog.getLatestAlertDialog();
            android.widget.NumberPicker minutes=(android.widget.NumberPicker)findDescription(
                    chooser.getWindow().getDecorView(),"Delay minutes");
            minutes.setValue(10);
            chooser.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            root=activity.get().getWindow().getDecorView();
            findText(root,"When: In 10 minutes  ▾").performClick();
            chooser=ShadowAlertDialog.getLatestAlertDialog();
            chooser.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            root=activity.get().getWindow().getDecorView();
            assertNotNull(findText(root,"When: Now  ▾"));
            firstEntry(root).setText("Untimed task");
            findText(root,"Add").performClick();
            assertEquals(0,store.activePersonal().get(0).dueAt);
        }
    }

    @Test public void dateValidationRejectsPastOrImpossibleTimesAndAllowsCalendarDays() {
        ZoneId berlin=ZoneId.of("Europe/Berlin");
        ZonedDateTime now=ZonedDateTime.of(2026,11,1,9,0,0,0,berlin);
        assertThrows(IllegalArgumentException.class,
                ()->QuickDateTimePicker.at(LocalDate.of(2026,11,1),8,59,berlin,
                        now.toInstant().toEpochMilli()));
        assertThrows(IllegalArgumentException.class,
                ()->QuickDateTimePicker.at(LocalDate.of(2027,3,28),2,30,berlin,
                        now.toInstant().toEpochMilli()));
        long appointment=QuickDateTimePicker.at(LocalDate.of(2027,6,15),12,30,
                berlin,now.toInstant().toEpochMilli());
        assertEquals(ZonedDateTime.of(2027,6,15,12,30,0,0,berlin)
                .toInstant().toEpochMilli(),appointment);
    }

    private static View findDescription(View root,String expected){
        if(expected.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View result=findDescription(((ViewGroup)root).getChildAt(i),expected);
            if(result!=null)return result;
        }
        return null;
    }
    private static TextView findText(View root,String expected){
        if(root instanceof TextView&&expected.equals(((TextView)root).getText().toString()))
            return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            TextView result=findText(((ViewGroup)root).getChildAt(i),expected);
            if(result!=null)return result;
        }
        return null;
    }
    private static EditText firstEntry(View root){
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            EditText result=firstEntry(((ViewGroup)root).getChildAt(i));
            if(result!=null)return result;
        }
        return null;
    }
}
