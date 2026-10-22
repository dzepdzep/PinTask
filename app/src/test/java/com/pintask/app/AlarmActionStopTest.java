package com.pintask.app;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Done and Snooze must stop the actual alarm audio service, not just the card. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AlarmActionStopTest {
    private Context app;
    private TrackingContext tracking;
    private static final class TrackingContext extends ContextWrapper {
        int stops=0;
        String lastStopped;
        TrackingContext(Context base){super(base);}
        @Override public boolean stopService(Intent service) {
            stops++;
            ComponentName target=service.getComponent();
            lastStopped=target==null?null:target.getClassName();
            return true;
        }
    }
    @Before public void setUp() {
        app=RuntimeEnvironment.getApplication();
        AlarmSoundService.stop(app);
        app.deleteDatabase("pintask.db");
        app.getSharedPreferences("settings",0).edit().clear().commit();
        app.getSharedPreferences("settings",0).edit()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
        tracking=new TrackingContext(app);
        TaskNotifications.ensureChannel(app);
    }

    @Test public void doneButtonCompletesTaskAndStopsItsForegroundRingtone() {
        AlarmSoundService.setEnabled(app,true);
        try(TaskStore store=new TaskStore(app)){
            TaskStore.Task task=store.add("Stop ringtone with Done");
            assertTrue(AlarmSoundService.start(app,task.id));
            Intent done=new Intent(app,TaskActionReceiver.class)
                    .setAction(TaskNotifications.ACTION_DONE)
                    .putExtra(TaskNotifications.EXTRA_ID,task.id);
            new TaskActionReceiver().onReceive(tracking,done);
            assertEquals(1,tracking.stops);
            assertEquals(AlarmSoundService.class.getName(),tracking.lastStopped);
            assertTrue(store.find(task.id).done);
            // Repeated Done must not unnecessarily restart or stop another alarm.
            new TaskActionReceiver().onReceive(tracking,done);
            assertEquals(1,tracking.stops);
        }
    }

    @Test public void snoozeNotificationCancellationStopsAlarmWithoutCompletingTask(){
        AlarmSoundService.setEnabled(app,true);
        try(TaskStore store=new TaskStore(app)){
            TaskStore.Task task=store.add("Snooze a ringing task",1,
                    System.currentTimeMillis()-2000,TaskStore.REPEAT_NEVER,0);
            assertTrue(AlarmSoundService.start(app,task.id));
            TaskStore.Task snoozed=store.snooze(task.id,System.currentTimeMillis()+300_000);
            TaskNotifications.cancel(tracking,task.id);
            assertNotNull(snoozed);
            assertFalse(store.find(task.id).done);
            assertTrue(store.find(task.id).snoozedUntil>System.currentTimeMillis());
            assertEquals(1,tracking.stops);
        }
    }

    @Test public void unrelatedDoneDoesNotStopAnotherTasksRingtone(){
        AlarmSoundService.setEnabled(app,true);
        try(TaskStore store=new TaskStore(app)){
            TaskStore.Task due=store.add("Ringing",1,System.currentTimeMillis()-1000,
                    TaskStore.REPEAT_NEVER,0);
            TaskStore.Task unrelated=store.add("Not ringing");
            assertTrue(AlarmSoundService.start(app,due.id));
            TaskNotifications.cancel(tracking,unrelated.id);
            assertEquals(0,tracking.stops);
            TaskNotifications.cancel(tracking,due.id);
            assertEquals(1,tracking.stops);
        }
    }

    @Test public void concurrentAlarmsKeepSoundUntilAllHaveBeenHandled(){
        AlarmSoundService.setEnabled(app,true);
        assertTrue(AlarmSoundService.start(app,601));
        assertTrue(AlarmSoundService.start(app,602));
        AlarmSoundService.stopForTask(tracking,601);
        assertEquals(0,tracking.stops);
        AlarmSoundService.stopForTask(tracking,602);
        assertEquals(1,tracking.stops);
        AlarmSoundService.stopForTask(tracking,602);
        assertEquals(1,tracking.stops);
    }

    @Test public void turningOffDirectAlarmStopsServiceWithoutStartingNewBackgroundService(){
        AlarmSoundService.setEnabled(app,true);
        assertTrue(AlarmSoundService.start(app,704));
        AlarmSoundService.setEnabled(tracking,false);
        assertEquals(1,tracking.stops);
        assertFalse(AlarmSoundService.enabled(app));
    }
}
