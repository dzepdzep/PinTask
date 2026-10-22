package com.pintask.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class StatusBarIconTest {
    private Context context;
    private NotificationManager manager;

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings",0).edit().clear().commit();
        context.deleteDatabase("pintask.db");
        manager=context.getSystemService(NotificationManager.class);
        manager.cancelAll();
    }

    @Test public void togglePostsAndRemovesOnlyShortcutNotification(){
        TaskNotifications.ensureChannel(context);
        TaskNotifications.setStatusIconEnabled(context,true);
        assertTrue(TaskNotifications.statusIconEnabled(context));
        assertEquals(0,countStatus());
        assertTrue(TaskNotifications.statusIconState(context),
                TaskNotifications.statusIconState(context).startsWith("Waiting"));
        TaskNotifications.setStatusIconEnabled(context,false);
        assertFalse(TaskNotifications.statusIconEnabled(context));
        assertEquals(0,countStatus());
        assertTrue(TaskNotifications.statusIconState(context).startsWith("Off"));
    }

    @Test public void upcomingTaskShowsIconOnlyWhenItsEarlyReminderActuallyFires() {
        TaskNotifications.setStatusIconEnabled(context,true);
        try(TaskStore store=new TaskStore(context)){
            long due=System.currentTimeMillis()+90_000L;
            TaskStore.Task task=store.add("Soon",1,due,TaskStore.REPEAT_NEVER,2);
            TaskNotifications.syncAll(context);
            assertEquals("Do not pre-post before alarm callback",0,countStatus());
            ReminderScheduler.deliver(context,task.id,"alarm");
            assertEquals(1,countStatus());
            assertTrue(TaskNotifications.statusIconState(context).startsWith("Posted"));
            TaskStore.Completion completion=store.complete(task.id);
            assertNotNull(completion);
            TaskNotifications.cancel(context,task.id);
            assertEquals("Completed task must clear the indicator",0,countStatus());
        }
    }

    @Test public void snoozingClearsBadgeUntilTheNewReminderFires() {
        TaskNotifications.setStatusIconEnabled(context,true);
        try(TaskStore store=new TaskStore(context)){
            TaskStore.Task task=store.add("Snooze me",1,
                    System.currentTimeMillis()-1500,TaskStore.REPEAT_NEVER,0);
            ReminderScheduler.deliver(context,task.id,"alarm");
            assertEquals(1,countStatus());
            TaskStore.Task snoozed=store.snooze(task.id,System.currentTimeMillis()+300_000L);
            TaskNotifications.cancel(context,task.id);
            assertEquals(0,countStatus());
            assertFalse(TaskNotifications.needsAttention(snoozed,System.currentTimeMillis()));
        }
    }

    @Test public void disabledAndUntimedTasksDoNotTriggerTheIcon() {
        TaskNotifications.setStatusIconEnabled(context,true);
        try(TaskStore store=new TaskStore(context)){
            store.add("Unscheduled");
            store.add("Intentionally silent",1,System.currentTimeMillis()-2500,
                    TaskStore.REPEAT_NEVER,TaskStore.REMINDER_OFF);
            TaskNotifications.showStatusIcon(context);
            assertEquals(0,countStatus());
        }
    }

    @Test public void disablingShortcutLeavesQuickAddAlone(){
        TaskNotifications.setQuickAddEnabled(context,true);
        TaskNotifications.setStatusIconEnabled(context,true);
        assertEquals(0,countStatus());
        TaskNotifications.setStatusIconEnabled(context,false);
        assertEquals(0,countStatus());
        boolean quickAddExists=false;
        for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications()){
            if("PinTask · Quick Add".contentEquals(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)))
                quickAddExists=true;
        }
        assertTrue("Quick Add setting must remain independent",quickAddExists);
    }

    @Test public void statusIconDoesNotUseOrMuteTimedAlarmChannel(){
        TaskNotifications.ensureChannel(context);
        TaskNotifications.setStatusIconEnabled(context,true);
        assertNotNull(manager.getNotificationChannel(TaskNotifications.REMINDER_CHANNEL).getSound());
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
                manager.getNotificationChannel(TaskNotifications.REMINDER_CHANNEL).getImportance());
        assertNull(manager.getNotificationChannel(TaskNotifications.STATUS_CHANNEL).getSound());
    }

    private int countStatus(){
        int count=0;
        for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications()){
            if(TaskNotifications.STATUS_CHANNEL.equals(n.getNotification().getChannelId()))count++;
        }
        return count;
    }
}
