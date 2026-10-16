package com.pintask.app;

import android.app.*;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ReminderSchedulerTest {
    Context context;
    TaskStore store;
    AlarmManager alarms;
    NotificationManager notifications;

    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().commit();
        store=new TaskStore(context);
        alarms=context.getSystemService(AlarmManager.class);
        notifications=context.getSystemService(NotificationManager.class);
        notifications.cancelAll();
    }
    @After public void close(){store.close();}

    @Test public void threeSimultaneousAlarmsHaveIndependentIdentitiesAndCancellation() {
        long time=System.currentTimeMillis()+600_000;
        TaskStore.Task a=store.add("A",1,time,0,0);
        TaskStore.Task b=store.add("B",1,time,0,0);
        TaskStore.Task c=store.add("C",1,time,0,0);
        ReminderScheduler.rescheduleAll(context);
        assertEquals(3,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        ReminderScheduler.schedule(context,b);
        assertEquals(3,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        ReminderScheduler.cancel(context,b.id);
        assertEquals(2,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        java.util.Set<Long> ids=new java.util.HashSet<>();
        for(var alarm:Shadows.shadowOf(alarms).getScheduledAlarms())
            ids.add(Shadows.shadowOf(alarm.operation).getSavedIntent().getLongExtra(TaskNotifications.EXTRA_ID,0));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(a.id,c.id)),ids);
    }

    @Test public void oneBroadcastPostsAllThreeDueCardsAndRepeatsDoNotRepost() {
        long time=System.currentTimeMillis()-1000;
        TaskStore.Task a=store.add("A",1,time,0,0);
        TaskStore.Task b=store.add("B",1,time,0,0);
        TaskStore.Task c=store.add("C",1,time,0,0);
        TaskStore.Task future=store.add("Future",1,time+600_000,0,0);
        Intent alarm=new Intent(context,TaskActionReceiver.class).setAction(TaskNotifications.ACTION_REMINDER).putExtra(TaskNotifications.EXTRA_ID,a.id);
        new TaskActionReceiver().onReceive(context,alarm);
        assertEquals(3,notifications.getActiveNotifications().length);
        for(TaskStore.Task task:new TaskStore.Task[]{a,b,c}) {
            assertTrue(store.find(task.id).remindedAt>0);
            assertEquals(task.title,Shadows.shadowOf(notifications).getNotification((int)task.id).extras.getString(Notification.EXTRA_TITLE));
        }
        assertEquals(0,store.find(future.id).remindedAt);
        notifications.cancel((int)b.id);
        new TaskActionReceiver().onReceive(context,alarm);
        assertEquals(2,notifications.getActiveNotifications().length);
    }

    @Test public void blockedChannelDoesNotConsumeReminder() {
        notifications.createNotificationChannel(new NotificationChannel(TaskNotifications.REMINDER_CHANNEL,"Blocked",NotificationManager.IMPORTANCE_NONE));
        TaskStore.Task task=store.add("Blocked",1,System.currentTimeMillis()-1000,0,0);
        ReminderScheduler.deliverDue(context);
        assertEquals(0,store.find(task.id).remindedAt);
        assertEquals(0,notifications.getActiveNotifications().length);
    }

    @Test public void reschedulesBeyondOneThousandTasks() {
        long time=System.currentTimeMillis()+600_000;
        var db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            for(int i=0;i<1001;i++)TaskStore.insert(db,"Task "+i,System.currentTimeMillis(),1,time,0,0,0,"");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        ReminderScheduler.rescheduleAll(context);
        assertEquals(1001,Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }

    @Test @Config(sdk=31) public void deniedExactAccessKeepsInexactAlarm() {
        Shadows.shadowOf(alarms).setCanScheduleExactAlarms(false);
        assertFalse(ReminderScheduler.canScheduleExact(context));
        ReminderScheduler.schedule(context,store.add("Fallback",1,System.currentTimeMillis()+600_000,0,0));
        assertEquals(1,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        assertNotEquals(0,Shadows.shadowOf(alarms).getScheduledAlarms().get(0).getWindowLengthMs());
    }

    @Test @Config(sdk=31) public void grantingAccessReplacesInexactWithExactAlarm() {
        Shadows.shadowOf(alarms).setCanScheduleExactAlarms(false);
        ReminderScheduler.schedule(context,store.add("Exact",1,System.currentTimeMillis()+600_000,0,0));
        Shadows.shadowOf(alarms).setCanScheduleExactAlarms(true);
        new BootReceiver().onReceive(context,new Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
        assertEquals(1,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        assertEquals(0,Shadows.shadowOf(alarms).getScheduledAlarms().get(0).getWindowLengthMs());
    }
}
