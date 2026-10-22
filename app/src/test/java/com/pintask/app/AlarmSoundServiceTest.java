package com.pintask.app;

import android.app.NotificationManager;
import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Regression tests for deliberate opt-in alarm ringing and no duplicate defaults. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AlarmSoundServiceTest {
    private Context context;

    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("interactive_shift_guide_v29",true)
                .putBoolean("startup_access_reviewed_v31",true).commit();
        TaskNotifications.ensureChannel(context);
    }

    @Test public void ringtonePlaybackIsOffUntilUserExplicitlyEnablesIt() {
        assertFalse(AlarmSoundService.enabled(context));
        assertFalse(AlarmSoundService.start(context,73));
        assertFalse(ReminderAudit.summary(context).contains("ringtone_service_requested #73"));
    }

    @Test public void onTimeAlarmRequestsSeparateRingingOnlyWhenEnabled() {
        AlarmSoundService.setEnabled(context,true);
        assertTrue(AlarmSoundService.enabled(context));
        try(TaskStore store=new TaskStore(context)) {
            TaskStore.Task task=store.add("Ring when due",TaskStore.PRIORITY_NORMAL,
                    System.currentTimeMillis()-1500,TaskStore.REPEAT_NEVER,0);
            ReminderScheduler.deliver(context,task.id,"alarm");
            assertEquals(1,store.activeAll().size());
            assertTrue(store.find(task.id).remindedAt>0);
            String report=ReminderAudit.summary(context);
            assertTrue(report,report.contains("delivered_alarm #"+task.id));
            assertTrue(report,report.contains("ringtone_service_requested #"+task.id));
        }
    }

    @Test public void staleRecoveryDoesNotRingAndDoesNotDuplicate() {
        AlarmSoundService.setEnabled(context,true);
        try(TaskStore store=new TaskStore(context)) {
            TaskStore.Task task=store.add("Old missed reminder",TaskStore.PRIORITY_NORMAL,
                    System.currentTimeMillis()-30*60_000L,TaskStore.REPEAT_NEVER,0);
            ReminderScheduler.deliver(context,task.id,"watchdog");
            String report=ReminderAudit.summary(context);
            assertTrue(report,report.contains("delivered_watchdog #"+task.id));
            assertFalse(report,report.contains("ringtone_service_requested #"+task.id));
            ReminderScheduler.deliver(context,task.id,"alarm");
            assertFalse(ReminderAudit.summary(context).contains("ringtone_service_requested #"+task.id));
        }
    }

    @Test public void disablingRingtoneDoesNotDisableAlarmNotificationChannel() {
        AlarmSoundService.setEnabled(context,true);
        AlarmSoundService.setEnabled(context,false);
        assertFalse(AlarmSoundService.enabled(context));
        NotificationManager nm=context.getSystemService(NotificationManager.class);
        assertNotNull(nm.getNotificationChannel(TaskNotifications.REMINDER_CHANNEL));
        assertNotNull(nm.getNotificationChannel(TaskNotifications.REMINDER_CHANNEL).getSound());
    }
}
