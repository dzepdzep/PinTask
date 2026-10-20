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
        context.getSharedPreferences("reminder_diagnostics_v36",0).edit().clear().commit();
        store=new TaskStore(context);
        alarms=context.getSystemService(AlarmManager.class);
        notifications=context.getSystemService(NotificationManager.class);
        notifications.cancelAll();
    }
    @After public void close(){store.close();}

    @Test public void upgradedInstallUsesAudibleAlarmSoundAndVibrationChannel() {
        TaskNotifications.ensureChannel(context);
        NotificationChannel channel=notifications.getNotificationChannel(TaskNotifications.REMINDER_CHANNEL);
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_HIGH,channel.getImportance());
        assertEquals(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,channel.getSound());
        assertNotNull(channel.getAudioAttributes());
        assertEquals(android.media.AudioAttributes.USAGE_ALARM,channel.getAudioAttributes().getUsage());
        assertTrue(channel.shouldVibrate());
        TaskStore.Task task=store.add("Alarm should sound",1,System.currentTimeMillis()-1500,0,0);
        ReminderScheduler.deliverDue(context);
        assertEquals(1,notifications.getActiveNotifications().length);
        android.app.Notification sent=Shadows.shadowOf(notifications).getNotification((int)task.id);
        assertNotNull(sent);
        assertEquals(TaskNotifications.REMINDER_CHANNEL,sent.getChannelId());
        assertEquals(task.id,store.find(task.id).id);
        assertTrue(store.find(task.id).remindedAt>0);
    }

    @Test public void intentionallyConfirmedRecoveryFixesOldSilentTasksOnly() {
        long next=System.currentTimeMillis()+300_000;
        TaskStore.Task silent=store.add("Silent old",1,next,0,TaskStore.REMINDER_OFF);
        TaskStore.Task alreadyOn=store.add("Enabled",1,next,0,0);
        TaskStore.Task untimed=store.add("Untimed",1,0,0,TaskStore.REMINDER_OFF);
        assertEquals(TaskStore.REMINDER_OFF,store.find(silent.id).notifyBeforeMinutes);
        assertEquals(1,ReminderHealth.enableExistingTimed(context));
        assertEquals(0,store.find(silent.id).notifyBeforeMinutes);
        assertEquals(0,store.find(alreadyOn.id).notifyBeforeMinutes);
        assertEquals(TaskStore.REMINDER_OFF,store.find(untimed.id).notifyBeforeMinutes);
        assertEquals(2,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        assertEquals(0,ReminderHealth.enableExistingTimed(context));
    }

    @Test public void aScheduledAlarmFromReceiverDeliversAllThreeWithAudibleChannel() {
        long due=System.currentTimeMillis()-2000;
        for(int i=0;i<3;i++)store.add("Patient task "+i,1,due,0,0);
        new TaskActionReceiver().onReceive(context,new Intent(context,TaskActionReceiver.class)
                .setAction(TaskNotifications.ACTION_REMINDER).putExtra(TaskNotifications.EXTRA_ID,1L));
        assertEquals(3,notifications.getActiveNotifications().length);
        for(var posted:notifications.getActiveNotifications()) {
            assertEquals(TaskNotifications.REMINDER_CHANNEL,posted.getNotification().getChannelId());
        }
    }

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

    @Test public void silentOrLowImportanceChannelIsNotReportedAudible() {
        TaskNotifications.ensureChannel(context);
        String id=TaskNotifications.activeReminderChannel(context);
        assertTrue(TaskNotifications.canAlertAudibly(context,id));
        notifications.deleteNotificationChannel(id);
        NotificationChannel quiet=new NotificationChannel(id,"Muted",NotificationManager.IMPORTANCE_LOW);
        quiet.setSound(null,null);
        notifications.createNotificationChannel(quiet);
        assertTrue(TaskNotifications.canNotifyChannel(context,id));
        assertFalse(TaskNotifications.canAlertAudibly(context,id));
        assertEquals(StartupAccess.ALARM_CHANNEL,StartupAccess.nextRequired(context));
    }

    @Test public void syncDoesNotPrePostAndSilenceAnOverdueFirstAlarm() {
        TaskStore.Task task=store.add("Overdue alarm",1,System.currentTimeMillis()-2000,0,0);
        TaskNotifications.syncAll(context);
        assertEquals(0,notifications.getActiveNotifications().length);
        ReminderScheduler.rescheduleAll(context);
        assertNotNull(Shadows.shadowOf(notifications).getNotification((int)task.id));
        assertTrue(store.find(task.id).remindedAt>0);
        assertTrue(ReminderAudit.summary(context).contains("delivered_reschedule_overdue"));
    }

    @Test public void scheduleAuditIncludesOneTimeReminders() {
        TaskStore.Task task=store.add("One time",1,System.currentTimeMillis()+600_000,0,0);
        ReminderScheduler.schedule(context,task);
        String report=ReminderAudit.summary(context);
        assertTrue(report,report.contains("scheduled_exact #"+task.id));
    }

    @Test public void auditSeparatesLateAlarmCallbackFromRecovery() {
        TaskStore.Task task=store.add("Late",1,System.currentTimeMillis()-300_000,0,0);
        ReminderScheduler.deliverDue(context,"alarm");
        String report=ReminderAudit.summary(context);
        assertTrue(report.contains("late_delivery_alarm #"+task.id));
        assertFalse(report.contains("recovered_by_alarm #"+task.id));
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
