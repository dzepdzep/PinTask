package com.pintask.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class StartupAccessTest {
    Context app;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();
        app.deleteDatabase("pintask.db");
        app.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("interactive_shift_guide_v29",true).commit();
    }

    @Test public void startupAuditRecognizesAllCorePermissionsWhenAllowed(){
        TaskNotifications.ensureChannel(app);
        assertEquals(StartupAccess.READY,StartupAccess.nextRequired(app));
        assertTrue(StartupAccess.summary(app).contains("Exact alarm access: Allowed"));
        assertTrue(StartupAccess.summary(app).contains("Battery restrictions: "));
        assertTrue(StartupAccess.summary(app).contains("Manufacturer auto-start/background: Check manually"));
    }

    @Test public void blockedTimedAlarmCategoryIsReportedAndOpensCorrectSettings(){
        NotificationManager notifications=app.getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
            TaskNotifications.REMINDER_CHANNEL,"Timed test",NotificationManager.IMPORTANCE_NONE));
        assertEquals(StartupAccess.ALARM_CHANNEL,StartupAccess.nextRequired(app));
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertEquals("PinTask · Reminder access needed",dialog.getTitle());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Intent intent=Shadows.shadowOf((android.app.Application)app).getNextStartedActivity();
            assertNotNull(intent);
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,intent.getAction());
            assertEquals(TaskNotifications.REMINDER_CHANNEL,
                    intent.getStringExtra(Settings.EXTRA_CHANNEL_ID));
        }
    }

    @Test @Config(sdk=31) public void missingExactAlarmsAreIdentified(){
        Shadows.shadowOf(app.getSystemService(AlarmManager.class)).setCanScheduleExactAlarms(false);
        assertEquals(StartupAccess.EXACT_ALARMS,StartupAccess.nextRequired(app));
        assertTrue(StartupAccess.summary(app).contains("Exact alarm access: Needs attention"));
    }

    @Test @Config(sdk=33) public void notificationRuntimePermissionIsCheckedBeforeAnythingElse(){
        Shadows.shadowOf((android.app.Application)app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertEquals(StartupAccess.RUNTIME_NOTIFICATIONS,StartupAccess.nextRequired(app));
        Shadows.shadowOf((android.app.Application)app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertNotEquals(StartupAccess.RUNTIME_NOTIFICATIONS,StartupAccess.nextRequired(app));
    }
}
