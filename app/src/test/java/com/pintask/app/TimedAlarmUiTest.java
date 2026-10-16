package com.pintask.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.AlarmManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class TimedAlarmUiTest {
    Context context;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("interactive_shift_guide_v29",true).commit();
    }

    @Test public void timedTaskDefaultsToAlarmAndActuallySchedulesAnAlarm() {
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)) {
            View root=controller.get().getWindow().getDecorView();
            find(root,"Add with time or repeat").performClick();
            root=controller.get().getWindow().getDecorView();
            entry(root).setText("Important timed reminder");
            chooseTomorrow(controller.get());
            root=controller.get().getWindow().getDecorView();
            find(root,"Time · Optional").performClick();
            TimePickerDialog picker=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            picker.getTimePicker().setHour(10);
            picker.getTimePicker().setMinute(0);
            picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"Alarm: At task time"));
            find(root,"More options").performClick();
            root=controller.get().getWindow().getDecorView();
            assertEquals("At task time",reminderSpinner(root).getSelectedItem());
            find(root,"Save task").performClick();
            assertEquals(1,store.activePersonal().size());
            assertEquals(0,store.activePersonal().get(0).notifyBeforeMinutes);
            assertTrue(store.activePersonal().get(0).reminderEnabled());
            assertEquals(1,Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
        }
    }

    @Test public void deliberateNoReminderSelectionRemainsPossible() {
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup();
            TaskStore store=new TaskStore(context)) {
            find(controller.get().getWindow().getDecorView(),"Add with time or repeat").performClick();
            entry(controller.get().getWindow().getDecorView()).setText("Intentionally silent");
            chooseTomorrow(controller.get());
            find(controller.get().getWindow().getDecorView(),"Time · Optional").performClick();
            TimePickerDialog picker=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            picker.getTimePicker().setHour(11);
            picker.getTimePicker().setMinute(0);
            picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            find(controller.get().getWindow().getDecorView(),"More options").performClick();
            reminderSpinner(controller.get().getWindow().getDecorView()).setSelection(0);
            find(controller.get().getWindow().getDecorView(),"Save task").performClick();
            assertEquals(TaskStore.REMINDER_OFF,store.activePersonal().get(0).notifyBeforeMinutes);
            assertEquals(0,Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
        }
    }

    private void chooseTomorrow(ShiftActivity a){
        find(a.getWindow().getDecorView(),"Change date").performClick();
        DatePickerDialog pick=(DatePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
        LocalDate tomorrow=LocalDate.now().plusDays(1);
        pick.updateDate(tomorrow.getYear(),tomorrow.getMonthValue()-1,tomorrow.getDayOfMonth());
        pick.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    private static Spinner reminderSpinner(View root){
        if(root instanceof Spinner){
            Spinner spinner=(Spinner)root;
            if(spinner.getCount()>1&&"No reminder".equals(spinner.getItemAtPosition(0)))
                return spinner;
        }
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            Spinner found=reminderSpinner(((ViewGroup)root).getChildAt(i));
            if(found!=null)return found;
        }
        return null;
    }
    private static EditText entry(View root){
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            EditText found=entry(((ViewGroup)root).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private static TextView find(View root,String name){
        if(root instanceof TextView&&name.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            TextView found=find(((ViewGroup)root).getChildAt(i),name);if(found!=null)return found;
        }
        return null;
    }
}
