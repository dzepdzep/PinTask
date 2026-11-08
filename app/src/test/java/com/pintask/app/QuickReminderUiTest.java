package com.pintask.app;

import android.app.*;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Looper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Quick Add shows the actual alarm time and saves the same early offset. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class QuickReminderUiTest {
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

    @Test public void tomorrowEarlyAlertIsVisibleAndScheduledFiveMinutesBefore(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();
            firstEdit(root).setText("Turn off computer");
            find(root,"When: Now  ▾").performClick();
            AlertDialog when=ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(4,when.getListView().getAdapter().getCount());
            when.getListView().performItemClick(null,2,2);
            TimePickerDialog clock=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            clock.updateTime(17,30);
            clock.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            LocalDate day=LocalDate.now().plusDays(1);
            long due=QuickDateTimePicker.at(day,17,30,ZoneId.systemDefault(),
                    System.currentTimeMillis());
            root=controller.get().getWindow().getDecorView();
            assertEquals("Turn off computer",firstEdit(root).getText().toString());
            assertNotNull(find(root,"Remind me: At task time  ▾"));
            assertNotNull(find(root,"Task due: "+time(due)));
            assertNotNull(find(root,"Alarm rings: "+time(due)));

            find(root,"Remind me: At task time  ▾").performClick();
            AlertDialog advance=ShadowAlertDialog.getLatestAlertDialog();
            advance.getListView().performItemClick(null,1,1); // 5 minutes earlier
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"Remind me: 5 minutes earlier  ▾"));
            assertNotNull(find(root,"Task due: "+time(due)));
            assertNotNull(find(root,"Alarm rings: "+time(due-300_000L)));
            assertEquals("Turn off computer",firstEdit(root).getText().toString());

            find(root,"Add").performClick();
            TaskStore.Task task=store.activePersonal().get(0);
            assertEquals(due,task.dueAt);
            assertEquals(5,task.notifyBeforeMinutes);
            assertEquals(due-300_000L,task.notificationAt());
            assertEquals(1,Shadows.shadowOf(context.getSystemService(AlarmManager.class))
                    .getScheduledAlarms().size());
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"When: Now  ▾"));
        }
    }

    @Test public void countdownTooShortForAdvanceDoesNotSaveOrRingImmediately(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();
            firstEdit(root).setText("Water plants");
            find(root,"When: Now  ▾").performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,1,1);
            AlertDialog stopwatch=ShadowAlertDialog.getLatestAlertDialog();
            android.widget.NumberPicker minutes=(android.widget.NumberPicker)findDescription(
                    stopwatch.getWindow().getDecorView(),"Delay minutes");
            minutes.setValue(3);
            stopwatch.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            root=controller.get().getWindow().getDecorView();
            find(root,"Remind me: At task time  ▾").performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,1,1);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"⚠ Early reminder would be in the past — choose another time."));
            find(root,"Add").performClick();
            assertTrue("Invalid advance must not auto-create a task",store.activeAll().isEmpty());
            assertEquals("Water plants",firstEdit(root).getText().toString());
            find(root,"Remind me: 5 minutes earlier  ▾").performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,0,0);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"Remind me: At task time  ▾"));
            find(root,"Add").performClick();
            TaskStore.Task task=store.activeAll().get(0);
            assertEquals(0,task.notifyBeforeMinutes);
            assertTrue(task.notificationAt()>System.currentTimeMillis());
        }
    }

    @Test public void nowAndAtTimeRemainUnchanged(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"Add now · No timed alarm"));
            assertNull(find(root,"Remind me: At task time  ▾"));
            firstEdit(root).setText("Check mail");
            find(root,"Add").performClick();
            TaskStore.Task task=store.activePersonal().get(0);
            assertEquals(0,task.dueAt);
            assertEquals("Untimed quick tasks retain existing pinned notification behavior",
                    -1,task.notifyBeforeMinutes);
            assertEquals(0,Shadows.shadowOf(context.getSystemService(AlarmManager.class))
                    .getScheduledAlarms().size());
        }
    }

    @Test public void calculationRejectsPastAndOverLargeOffsets(){
        long now=1_000_000_000L;
        long due=QuickTaskTiming.dueAt(3600,0,now);
        assertEquals(now+3_600_000L,due);
        assertEquals(due-300_000L,QuickTaskTiming.alertAt(due,5));
        QuickTaskTiming.validate(due,5,now);
        assertThrows(IllegalArgumentException.class,()->QuickTaskTiming.validate(due,61,now));
        assertThrows(IllegalArgumentException.class,()->QuickTaskTiming.validate(now-1000L,0,now));
        assertThrows(IllegalArgumentException.class,()->QuickTaskTiming.validate(0,5,now));
        assertThrows(IllegalArgumentException.class,()->QuickTaskTiming.alertAt(due,1441));
        assertEquals(now+75_000L,QuickTaskTiming.dueAt(0,now+75_000L,now));
    }

    private static String time(long instant){
        return Instant.ofEpochMilli(instant).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"));
    }
    private static View findDescription(View view,String text){
        if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View result=findDescription(((ViewGroup)view).getChildAt(i),text);
            if(result!=null)return result;
        }
        return null;
    }
    private static TextView find(View view,String value){
        if(view instanceof TextView && value.equals(((TextView)view).getText().toString()))
            return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            TextView result=find(((ViewGroup)view).getChildAt(i),value);
            if(result!=null)return result;
        }
        return null;
    }
    private static EditText firstEdit(View view){
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            EditText result=firstEdit(((ViewGroup)view).getChildAt(i));
            if(result!=null)return result;
        }
        return null;
    }
}
