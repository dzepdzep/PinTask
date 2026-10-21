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
        manager=context.getSystemService(NotificationManager.class);
    }

    @Test public void togglePostsAndRemovesOnlyShortcutNotification(){
        TaskNotifications.ensureChannel(context);
        TaskNotifications.setStatusIconEnabled(context,true);
        assertTrue(TaskNotifications.statusIconEnabled(context));
        assertEquals(1,countStatus());
        assertTrue(TaskNotifications.statusIconState(context),
                TaskNotifications.statusIconState(context).startsWith("Posted"));
        TaskNotifications.setStatusIconEnabled(context,false);
        assertFalse(TaskNotifications.statusIconEnabled(context));
        assertEquals(0,countStatus());
        assertTrue(TaskNotifications.statusIconState(context).startsWith("Off"));
    }

    @Test public void disablingShortcutLeavesQuickAddAlone(){
        TaskNotifications.setQuickAddEnabled(context,true);
        TaskNotifications.setStatusIconEnabled(context,true);
        assertEquals(1,countStatus());
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
