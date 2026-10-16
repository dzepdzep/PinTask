package com.pintask.app;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
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
public class RelativeDelayTest {
    Context context; TaskStore store; NotificationManager notifications;AlarmManager alarms;
    @Before public void setUp(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
            .putBoolean("interactive_shift_guide_v29",true)
            .putBoolean("startup_access_reviewed_v31",true).commit();
        store=new TaskStore(context);
        notifications=context.getSystemService(NotificationManager.class);
        alarms=context.getSystemService(AlarmManager.class);
        notifications.cancelAll();
    }
    @After public void close(){store.close();}

    @Test public void presetsAlwaysIncludeFiveTenFifteenThirtyAndOneHour(){
        assertArrayEquals(new int[]{0,5,10,15,30,60},RelativeDelay.MINUTES);
        assertEquals("In 10 minutes",RelativeDelay.label(10));
        assertEquals("In 2 hours",RelativeDelay.label(120));
    }

    @Test public void customInputAcceptsMinutesHoursAndMixedDurations(){
        assertEquals(60,RelativeDelay.parse("60 minutes"));
        assertEquals(90,RelativeDelay.parse("90 minutes"));
        assertEquals(120,RelativeDelay.parse("2 hours"));
        assertEquals(90,RelativeDelay.parse("1.5 hours"));
        assertEquals(150,RelativeDelay.parse("2h 30m"));
        assertEquals(90,RelativeDelay.parse("1 hour and 30 minutes"));
        assertEquals(90,RelativeDelay.parse("90"));
        assertThrows(IllegalArgumentException.class,()->RelativeDelay.parse("20:00"));
        assertThrows(IllegalArgumentException.class,()->RelativeDelay.parse("zero"));
        assertThrows(IllegalArgumentException.class,()->RelativeDelay.parse("0"));
        assertThrows(IllegalArgumentException.class,()->RelativeDelay.parse("-1 minutes"));
        assertThrows(IllegalArgumentException.class,()->RelativeDelay.parse("100000 minutes"));
    }

    @Test public void stopwatchHoursMinutesAndSecondsValidateFullRange(){
        assertEquals(90*60+12,StopwatchDelayPicker.toSeconds(1,30,12));
        assertEquals(720*3600,StopwatchDelayPicker.toSeconds(720,0,0));
        assertEquals(1,StopwatchDelayPicker.toSeconds(0,0,1));
        assertThrows(IllegalArgumentException.class,()->StopwatchDelayPicker.toSeconds(0,0,0));
        assertThrows(IllegalArgumentException.class,()->StopwatchDelayPicker.toSeconds(720,1,0));
        assertThrows(IllegalArgumentException.class,()->StopwatchDelayPicker.toSeconds(1,60,0));
        assertEquals("In 1h 30m 12s",RelativeDelay.labelSeconds(5412));
        assertEquals("In 10 minutes",RelativeDelay.labelSeconds(600));
    }

    @Test public void stopwatchPickerDoesNotUseKeyboardAndSchedulesExactSeconds() {
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();
            find(root,"When: Now  ▾").performClick();
            AlertDialog menu=ShadowAlertDialog.getLatestAlertDialog();
            menu.getListView().performItemClick(null,6,6);
            AlertDialog stopwatch=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(stopwatch);
            NumberPicker hour=findWheel(stopwatch.getWindow().getDecorView(),"Delay hours");
            NumberPicker minute=findWheel(stopwatch.getWindow().getDecorView(),"Delay minutes");
            NumberPicker second=findWheel(stopwatch.getWindow().getDecorView(),"Delay seconds");
            assertNotNull(hour);assertNotNull(minute);assertNotNull(second);
            hour.setValue(1);
            minute.setValue(30);
            second.setValue(12);
            stopwatch.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"When: In 1h 30m 12s  ▾"));
            firstTextField(root).setText("Stopwatch seconds test");
            long before=System.currentTimeMillis();
            find(root,"Add").performClick();
            TaskStore.Task saved=store.activePersonal().get(0);
            assertTrue(saved.dueAt>=before+5412_000L);
            assertTrue(saved.dueAt<=System.currentTimeMillis()+5412_000L);
            assertEquals(0,saved.notifyBeforeMinutes);
            assertEquals(1,org.robolectric.Shadows.shadowOf(alarms).getScheduledAlarms().size());
            assertNull(org.robolectric.Shadows.shadowOf(notifications).getNotification((int)saved.id));
        }
    }

    @Test public void shiftStopwatchDelayPersistsSecondsAndBounds(){
        java.time.LocalDate now=java.time.LocalDate.now();
        int start=java.time.LocalTime.now().getHour()*60;
        try(ShiftRepository shifts=new ShiftRepository(context)){
            ShiftRepository.Template type=shifts.addTemplate("Short timer",start,(start+1439)%1440);
            ShiftRepository.Run run=shifts.scheduleCustom(now,type.id,false);
            long before=System.currentTimeMillis();
            shifts.addTaskAfterSeconds(run.id,"Shift seconds",77,0,0);
            TaskStore.Task saved=shifts.runTasks(run.id).get(0);
            assertTrue(saved.dueAt>=before+77_000L);
            assertTrue(saved.dueAt<=System.currentTimeMillis()+77_000L);
            assertThrows(IllegalArgumentException.class,
                    ()->shifts.addTaskAfterSeconds(run.id,"Too far",30*24*60*60,0,0));
            assertEquals(1,shifts.runTasks(run.id).size());
        }
    }

    private static NumberPicker findWheel(View view,String text){
        if(view instanceof NumberPicker&&text.contentEquals(view.getContentDescription()))return (NumberPicker)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            NumberPicker p=findWheel(((ViewGroup)view).getChildAt(i),text);
            if(p!=null)return p;
        }
        return null;
    }

    @Test public void quickAddInTenMinutesCreatesOneScheduledAlarmNotImmediateNotification(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();
            find(root,"When: Now  ▾").performClick();
            AlertDialog select=ShadowAlertDialog.getLatestAlertDialog();
            select.getListView().performItemClick(null,2,2); // Now, 5, 10
            root=controller.get().getWindow().getDecorView();
            assertNotNull(find(root,"When: In 10 minutes  ▾"));
            firstTextField(root).setText("Ten minute task");
            long before=System.currentTimeMillis();
            find(root,"Add").performClick();
            TaskStore.Task task=store.activePersonal().get(0);
            assertEquals(0,task.notifyBeforeMinutes);
            assertTrue(task.dueAt>=before+600_000);
            assertTrue(task.dueAt<=System.currentTimeMillis()+600_000);
            assertTrue(task.reminderEnabled());
            assertEquals(1,org.robolectric.Shadows.shadowOf(alarms).getScheduledAlarms().size());
            assertNull(org.robolectric.Shadows.shadowOf(notifications).getNotification((int)task.id));
            assertNotNull(find(controller.get().getWindow().getDecorView(),"When: Now  ▾"));
        }
    }

    @Test public void shiftRelativeTasksRetainMillisecondAccuracyAndCannotRunAfterShift(){
        java.time.LocalDate now=java.time.LocalDate.now();
        int start=java.time.LocalTime.now().getHour()*60;
        int end=(start+1439)%1440;
        try(ShiftRepository repo=new ShiftRepository(context)){
            ShiftRepository.Template template=repo.addTemplate("My active shift",start,end);
            ShiftRepository.Run shift=repo.scheduleCustom(now,template.id,false);
            long before=System.currentTimeMillis();
            repo.addTaskAfter(shift.id,"Shift reminder",5,0,0);
            TaskStore.Task task=repo.runTasks(shift.id).get(0);
            assertTrue(task.dueAt>=before+300_000);
            assertTrue(task.dueAt<=System.currentTimeMillis()+300_000);
            assertEquals(0,task.notifyBeforeMinutes);
            assertThrows(IllegalArgumentException.class,
                ()->repo.addTaskAfter(shift.id,"Beyond shift",43200,0,0));
            assertEquals(1,repo.runTasks(shift.id).size());
            ReminderScheduler.schedule(context,task);
            assertEquals(1,org.robolectric.Shadows.shadowOf(alarms).getScheduledAlarms().size());
        }
    }

    @Test public void customShiftHoursUseRealTimePickerButtons(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup();
            ShiftRepository repo=new ShiftRepository(context)){
            final ShiftRepository.Template[] saved={null};
            CustomShiftDialog.show(controller.get(),repo,t->saved[0]=t);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog form=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(find(form.getWindow().getDecorView(),"08:00"));
            findDescription(form.getWindow().getDecorView(),"Choose shift begins").performClick();
            TimePickerDialog start=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            start.updateTime(20,0);
            start.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            findDescription(form.getWindow().getDecorView(),"Choose shift ends").performClick();
            TimePickerDialog end=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();
            end.updateTime(8,0);
            end.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("20:00",findDescription(form.getWindow().getDecorView(),"Choose shift begins").getClass()==Button.class?
                ((Button)findDescription(form.getWindow().getDecorView(),"Choose shift begins")).getText().toString():"");
            assertEquals("08:00",((Button)findDescription(form.getWindow().getDecorView(),"Choose shift ends")).getText().toString());
            form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNotNull(saved[0]);
            assertEquals(1200,saved[0].startMinute);
            assertEquals(720,saved[0].durationMinutes);
        }
    }

    private static View findDescription(View root,String description) {
        if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View child=findDescription(((ViewGroup)root).getChildAt(i),description);
            if(child!=null)return child;
        }
        return null;
    }
    private static TextView find(View root,String text) {
        if(root instanceof TextView && text.equals(((TextView)root).getText().toString()))
            return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            TextView found=find(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    private static EditText firstTextField(View root) {
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            EditText found=firstTextField(((ViewGroup)root).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
}
