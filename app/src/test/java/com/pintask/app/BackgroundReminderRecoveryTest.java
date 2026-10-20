package com.pintask.app;

import android.app.*;
import android.app.job.JobScheduler;
import android.content.*;
import android.os.Build;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class BackgroundReminderRecoveryTest {
    Context context;
    TaskStore store;
    NotificationManager notices;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().commit();
        context.getSharedPreferences("reminder_diagnostics_v36",0).edit().clear().commit();
        store=new TaskStore(context);
        notices=context.getSystemService(NotificationManager.class);
        notices.cancelAll();
        context.getSystemService(JobScheduler.class).cancel(ReminderWatchdog.JOB_ID);
        TaskNotifications.ensureChannel(context);
    }
    @After public void finish(){store.close();}

    @Test public void recurringFutureReminderCreatesIndependentPersistentRecoveryJob(){
        TaskStore.Task task=store.enableAutomaticRoutine(store.add("Morning round",1,
                System.currentTimeMillis()+120_000,TaskStore.REPEAT_DAILY,0).id);
        ReminderScheduler.schedule(context,task);
        android.app.job.JobInfo info=context.getSystemService(JobScheduler.class)
                .getPendingJob(ReminderWatchdog.JOB_ID);
        assertNotNull(info);
        assertTrue(info.isPersisted());
        assertEquals(ReminderWatchdog.PERIOD,info.getIntervalMillis());
        assertEquals(ReminderWatchdog.class.getName(),info.getService().getClassName());
        assertTrue(ReminderAudit.summary(context).contains("scheduled_exact"));
    }

    @Test public void backgroundCheckDeliversMissedRemindersWithoutOpeningActivity(){
        TaskStore.Task first=store.add("Due 07:15",1,
                System.currentTimeMillis()-300_000,0,0);
        assertNull(Shadows.shadowOf(notices).getNotification((int)first.id));
        assertEquals(0,store.find(first.id).remindedAt);
        ReminderWatchdog.check(context);
        assertNotNull(Shadows.shadowOf(notices).getNotification((int)first.id));
        assertTrue(store.find(first.id).remindedAt>0);
        String report=ReminderAudit.summary(context);
        assertTrue(report,report.contains("watchdog_check"));
        assertTrue(report,report.contains("overdue_recovered"));
        ReminderWatchdog.check(context);
        assertEquals(1,notices.getActiveNotifications().length);
    }

    @Test public void independentBackgroundCheckAdvancesHourlyRoutineExactlyOnce(){
        TaskStore.Task prior=store.enableAutomaticRoutine(store.add("Hours",1,
                System.currentTimeMillis()-120_000,5001,0).id);
        ReminderWatchdog.check(context);
        assertTrue(store.find(prior.id).remindedAt>0);
        assertEquals(2,store.activePersonal().size());
        ReminderWatchdog.check(context);
        assertEquals(2,store.activePersonal().size());
    }

    @Test public void alarmBroadcastRecordsDeliveryIndependentOfAnyActivity(){
        TaskStore.Task task=store.add("Background broadcast",1,
                System.currentTimeMillis()-1000,0,0);
        new TaskActionReceiver().onReceive(context,
                new Intent(context,TaskActionReceiver.class).setAction(TaskNotifications.ACTION_REMINDER)
                        .putExtra(TaskNotifications.EXTRA_ID,task.id));
        assertNotNull(Shadows.shadowOf(notices).getNotification((int)task.id));
        assertTrue(ReminderAudit.summary(context).contains("alarm_callback"));
        assertTrue(ReminderAudit.summary(context).contains("delivered"));
    }

    @Test public void popUpToggleRetainsSoundAndChangesNotificationImportanceChannel(){
        NotificationChannel urgent=notices.getNotificationChannel(TaskNotifications.REMINDER_CHANNEL);
        NotificationChannel standard=notices.getNotificationChannel(TaskNotifications.NO_POPUP_CHANNEL);
        assertEquals(NotificationManager.IMPORTANCE_HIGH,urgent.getImportance());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,standard.getImportance());
        assertEquals(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,standard.getSound());
        assertTrue(standard.shouldVibrate());

        TaskNotifications.setPopupEnabled(context,false);
        assertFalse(TaskNotifications.popupEnabled(context));
        TaskStore.Task quiet=store.add("Quiet popup preference",1,
                System.currentTimeMillis()-1000,0,0);
        ReminderScheduler.deliver(context,quiet.id);
        assertEquals(TaskNotifications.NO_POPUP_CHANNEL,
                Shadows.shadowOf(notices).getNotification((int)quiet.id).getChannelId());

        TaskNotifications.setPopupEnabled(context,true);
        TaskStore.Task pop=store.add("Show popup preference",1,
                System.currentTimeMillis()-1000,0,0);
        ReminderScheduler.deliver(context,pop.id);
        assertEquals(TaskNotifications.REMINDER_CHANNEL,
                Shadows.shadowOf(notices).getNotification((int)pop.id).getChannelId());
        assertFalse(store.find(quiet.id).done);
        assertFalse(store.find(pop.id).done);
    }
}
