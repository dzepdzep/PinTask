package com.pintask.app;

import android.content.Context;
import android.app.NotificationManager;
import android.app.AlarmManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SimpleSchedulerTest {
    Context context; TaskStore store; ShiftRepository shifts;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication(); context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().putBoolean("custom_shifts_guide_seen_v28",true).putBoolean("interactive_shift_guide_v29",true).putBoolean("startup_access_reviewed_v31",true).commit();
        store=new TaskStore(context); shifts=new ShiftRepository(context);
    }
    @After public void close() { shifts.close(); store.close(); }
    @Test public void scheduleNeedsNoTemplateAndFutureDoesNotHideCurrent() {
        ShiftRepository.Template t=shifts.addTemplate("Current",480,1200);
        shifts.addStep(t.id,"Keep me",-1,0,1,0);
        ShiftRepository.Run current=shifts.startShift(t.id,System.currentTimeMillis()-60000);
        ShiftRepository.Run future=shifts.schedule(LocalDate.now().plusDays(2),"TD");
        assertEquals(current.id,shifts.currentRun().id);
        assertEquals(0,future.templateId);
        assertTrue(shifts.runTasks(future.id).isEmpty());
        assertEquals(future.id,shifts.schedule(LocalDate.now().plusDays(2),"TD").id);
    }
    @Test public void overnightAndDstRepeatStopAtEight() {
        TimeZone previous=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        try {
            LocalDate day=LocalDate.of(LocalDate.now().getYear()+1,10,1);
            while(day.getMonthValue()==10) { if(day.plusDays(1).getDayOfWeek()==DayOfWeek.SUNDAY && day.plusDays(8).getMonthValue()!=10) break; day=day.plusDays(1); }
            ShiftRepository.Run night=shifts.schedule(day,"ND");
            assertEquals(day.plusDays(1).atTime(8,0),Instant.ofEpochMilli(night.endsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
            shifts.addTask(night.id,"Rounds",60,60,0);
            List<TaskStore.Task> tasks=shifts.runTasks(night.id);
            assertEquals(7,tasks.size());
            assertEquals(day.plusDays(1),Instant.ofEpochMilli(tasks.get(0).dueAt).atZone(ZoneId.systemDefault()).toLocalDate());
            for(TaskStore.Task task:tasks) assertTrue(task.dueAt<night.endsAt);
            assertThrows(IllegalArgumentException.class,()->shifts.addTask(night.id,"Outside",480,0,0));
        } finally { TimeZone.setDefault(previous); }
    }
    @Test public void changingDayNeverDeletesExistingTasks() {
        LocalDate day=LocalDate.now().plusDays(2);
        ShiftRepository.Run run=shifts.schedule(day,"TD1"); shifts.addTask(run.id,"Preserve",-1,0,-2);
        assertThrows(IllegalArgumentException.class,()->shifts.schedule(day,"Off"));
        assertEquals("Preserve",shifts.runTasks(run.id).get(0).title);
        assertEquals(run.id,shifts.schedule(day,"TD1").id);
        assertEquals(12*3600000L,run.endsAt-run.startsAt);
    }
    @Test public void offDoesNotHideNightAndDoesNotRequireHours() {
        LocalDate day=LocalDate.now().plusDays(2);
        ShiftRepository.Run night=shifts.schedule(day,"ND");
        ShiftRepository.Run off=shifts.schedule(day.plusDays(1),"Off");
        assertEquals("Off",off.label);
        assertThrows(IllegalArgumentException.class,()->shifts.addTask(off.id,"No shift",-1,0,-2));
        assertEquals("Off",shifts.schedule(day.plusDays(2),"Off").label);
        assertEquals(night.id,shifts.runsForDate(day).get(0).id);
    }
    @Test public void reminderOffSurvivesBackupAndCannotBeClaimedOrScheduled() throws Exception {
        TaskStore.Task silent=store.add("Silent",1,System.currentTimeMillis()+60000,1,-2);
        ReminderScheduler.schedule(context,silent); TaskNotifications.show(context,silent);
        assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().isEmpty());
        assertNull(Shadows.shadowOf(context.getSystemService(NotificationManager.class)).getNotification((int)silent.id));
        assertNull(store.claimDueReminder(silent.id));
        shifts.schedule(LocalDate.now().plusDays(2),"TD"); shifts.schedule(LocalDate.now().plusDays(3),"ND");
        String backup=BackupData.export(store); BackupData.restore(store,backup);
        assertEquals(-2,store.find(silent.id).notifyBeforeMinutes);
        assertEquals(1,shifts.runsForDate(LocalDate.now().plusDays(3)).size());
        assertEquals(-2,store.complete(silent.id).nextTask.notifyBeforeMinutes);
    }
    @Test public void legacyDatabaseAndNotificationsRemainIntact() throws Exception {
        TaskStore.Task old=store.add("Legacy",2,System.currentTimeMillis()+3600000,0,-1);
        ShiftRepository.Template template=shifts.addTemplate("Old template",1200,480);
        shifts.addStep(template.id,"Old task",0,60,1,15);
        String backup=BackupData.export(store);
        shifts.schedule(LocalDate.now().plusDays(3),"TD");
        BackupData.restore(store,backup);
        assertEquals(old.id,store.find(old.id).id); assertEquals(-1,store.find(old.id).notifyBeforeMinutes);
        assertEquals(1,shifts.steps(template.id).size());
        ReminderScheduler.schedule(context,store.find(old.id));
        assertEquals(1,Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
    }
    @Test public void threeScreensAndTitleOnlyTaskSurviveRecreation() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();
            find(root,"Add with time or repeat").performClick();
            EditText entry=entry(controller.get().getWindow().getDecorView()); entry.setText("Simple task");
            controller.recreate(); root=controller.get().getWindow().getDecorView();
            assertEquals("Simple task",entry(root).getText().toString());
            find(root,"Save task").performClick();
            assertEquals(1,store.activePersonal().size()); assertEquals(-2,store.activePersonal().get(0).notifyBeforeMinutes);
            find(controller.get().getWindow().getDecorView(),"Work").performClick();
            find(controller.get().getWindow().getDecorView(),"Set a shift").performClick();
            android.app.DatePickerDialog datePicker=(android.app.DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();datePicker.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("＋ Create custom shift",dialog.getListView().getAdapter().getItem(0));
            assertEquals("Off · no shift",dialog.getListView().getAdapter().getItem(1));
        }
    }
    @Test public void rebootRestoresRemindersAndClaimsEachOccurrenceOnlyOnce() {
        TaskStore.Task future=store.add("Restore",1,System.currentTimeMillis()+3600000,0,15);
        ReminderScheduler.schedule(context,future);
        ReminderScheduler.cancel(context,future.id);
        new BootReceiver().onReceive(context,new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
        assertEquals(1,Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
        TaskStore.Task due=store.add("Due",1,System.currentTimeMillis()-1000,0,0);
        assertNotNull(store.claimDueReminder(due.id)); assertNull(store.claimDueReminder(due.id));
    }

    private TextView find(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText())) return (TextView)v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) { TextView found=find(((ViewGroup)v).getChildAt(i),text); if(found!=null)return found; }
        return null;
    }
    private EditText entry(View v) {
        if(v instanceof EditText)return (EditText)v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) { EditText found=entry(((ViewGroup)v).getChildAt(i));if(found!=null)return found; }
        return null;
    }
}
