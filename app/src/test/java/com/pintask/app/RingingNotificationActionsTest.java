package com.pintask.app;

import android.app.*;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/**
 * Real notification action receiver and snooze activity must stop foreground
 * playback, without changing what Done or Snooze means to the task database.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RingingNotificationActionsTest {
    Context context;
    @Before public void before() throws Exception {
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().commit();
        context.getSystemService(NotificationManager.class).cancelAll();
        setRequestedTask(0);
    }
    @After public void after() throws Exception {setRequestedTask(0);}

    private static void setRequestedTask(long id) throws Exception {
        java.lang.reflect.Field field=AlarmSoundService.class.getDeclaredField("requestedTaskId");
        field.setAccessible(true);
        field.setLong(null,id);
    }
    private static long requestedTask() throws Exception {
        java.lang.reflect.Field field=AlarmSoundService.class.getDeclaredField("requestedTaskId");
        field.setAccessible(true);
        return field.getLong(null);
    }

    @Test public void doneOnNotificationStopsRingingAndCompletesTask() throws Exception {
        try(TaskStore store=new TaskStore(context)) {
            TaskStore.Task task=store.add("Done should silence",1,
                    System.currentTimeMillis()-1000,TaskStore.REPEAT_NEVER,0);
            setRequestedTask(task.id);
            new TaskActionReceiver().onReceive(context,new Intent(context,TaskActionReceiver.class)
                    .setAction(TaskNotifications.ACTION_DONE)
                    .putExtra(TaskNotifications.EXTRA_ID,task.id));
            assertEquals(0,requestedTask());
            assertTrue(store.find(task.id).done);
            assertTrue(ReminderAudit.summary(context).contains(
                    "ringtone_stop_by_task_action #"+task.id));
        }
    }

    @Test public void completingDifferentTaskDoesNotSilenceAnotherAlarm() throws Exception {
        try(TaskStore store=new TaskStore(context)) {
            TaskStore.Task a=store.add("Alarm A");
            TaskStore.Task b=store.add("Alarm B");
            setRequestedTask(b.id);
            new TaskActionReceiver().onReceive(context,new Intent(context,TaskActionReceiver.class)
                    .setAction(TaskNotifications.ACTION_DONE)
                    .putExtra(TaskNotifications.EXTRA_ID,a.id));
            assertTrue(store.find(a.id).done);
            assertFalse(store.find(b.id).done);
            assertEquals(b.id,requestedTask());
        }
    }

    @Test public void openingSnoozeSilencesButDoesNotCompleteTask() throws Exception {
        try(TaskStore store=new TaskStore(context)) {
            TaskStore.Task task=store.add("Snooze instead",1,
                    System.currentTimeMillis()-2000,TaskStore.REPEAT_NEVER,0);
            setRequestedTask(task.id);
            Intent intent=new Intent(context,SnoozeActivity.class)
                    .putExtra(TaskNotifications.EXTRA_ID,task.id);
            try(var controller=Robolectric.buildActivity(SnoozeActivity.class,intent).setup()){
                assertEquals(0,requestedTask());
                assertFalse(store.find(task.id).done);
                assertTrue(ReminderAudit.summary(context).contains(
                        "ringtone_stop_by_task_action #"+task.id));
                AlertDialog choices=ShadowAlertDialog.getLatestAlertDialog();
                assertNotNull(choices);
                choices.getListView().performItemClick(null,0,0); // 5 minutes
                assertFalse(store.find(task.id).done);
                assertTrue(store.find(task.id).snoozedUntil>System.currentTimeMillis());
            }
        }
    }

    @Test public void notificationCancellationStopsOnlyItsOwnRingingTask() throws Exception {
        setRequestedTask(810203);
        TaskNotifications.cancel(context,810204);
        assertEquals(810203,requestedTask());
        TaskNotifications.cancel(context,810203);
        assertEquals(0,requestedTask());
    }
}
