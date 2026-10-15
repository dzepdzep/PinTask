package com.pintask.app;

import android.app.*;
import android.content.*;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WatchNotificationsTest {
    Context context;TaskStore store;NotificationManager manager;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");context.getSharedPreferences("settings",0).edit().clear().commit();store=new TaskStore(context);manager=context.getSystemService(NotificationManager.class);manager.cancelAll();}
    @After public void close(){store.close();}
    private Notification notification(int id){return Shadows.shadowOf(manager).getNotification(id);}
    private Notification.Action watchDone(Notification n){return new Notification.WearableExtender(n).getActions().get(0);}
    private void send(PendingIntent pending) throws Exception {new TaskActionReceiver().onReceive(context,Shadows.shadowOf(pending).getSavedIntent());Shadows.shadowOf(Looper.getMainLooper()).idle();}
    @Test public void taskCardsCanBridgeAndExposeOnlyBackgroundDoneOnWatch(){
        TaskStore.Task task=store.add("Watch task");TaskNotifications.show(context,task);Notification n=notification((int)task.id);
        assertTrue(TaskNotifications.watchEnabled(context));assertEquals(0,n.flags&(Notification.FLAG_LOCAL_ONLY|Notification.FLAG_ONGOING_EVENT|Notification.FLAG_NO_CLEAR));
        assertEquals(2,n.actions.length);assertEquals("Snooze",n.actions[1].title.toString());assertEquals(1,new Notification.WearableExtender(n).getActions().size());Notification.Action done=watchDone(n);assertEquals("Done",done.title.toString());Notification.Action.WearableExtender action=new Notification.Action.WearableExtender(done);assertFalse(action.getHintLaunchesActivity());assertFalse(action.isAvailableOffline());Intent intent=Shadows.shadowOf(done.actionIntent).getSavedIntent();assertEquals(TaskNotifications.ACTION_DONE,intent.getAction());assertEquals(task.id,intent.getLongExtra(TaskNotifications.EXTRA_ID,0));assertEquals(TaskActionReceiver.class.getName(),intent.getComponent().getClassName());
    }
    @Test public void dueAlertsAlsoExposeWatchDone(){
        TaskStore.Task task=store.add("Due",1,System.currentTimeMillis()-1000,0,-1);TaskNotifications.showDue(context,task);Notification n=notification(1000000000+(int)task.id);assertEquals(0,n.flags&(Notification.FLAG_LOCAL_ONLY|Notification.FLAG_ONGOING_EVENT|Notification.FLAG_NO_CLEAR));assertEquals("Done",watchDone(n).title.toString());assertEquals(task.id,Shadows.shadowOf(watchDone(n).actionIntent).getSavedIntent().getLongExtra(TaskNotifications.EXTRA_ID,0));
    }
    @Test public void watchDoneCompletesWithoutActivityAndClearsBothPhoneCards() throws Exception {
        TaskStore.Task task=store.add("Finish from wrist",1,System.currentTimeMillis()-1000,0,-1);TaskNotifications.show(context,task);TaskNotifications.showDue(context,task);PendingIntent callback=watchDone(notification((int)task.id)).actionIntent;send(callback);assertTrue(store.find(task.id).done);assertNull(notification((int)task.id));assertNull(notification(1000000000+(int)task.id));assertNull(Shadows.shadowOf((android.app.Application)context).getNextStartedActivity());
        Notification undo=null;for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications())if("undo".equals(active.getTag()))undo=active.getNotification();assertNotNull(undo);assertEquals("Undo",watchDone(undo).title.toString());assertEquals(0,undo.flags&Notification.FLAG_LOCAL_ONLY);send(watchDone(undo).actionIntent);assertFalse(store.find(task.id).done);assertNotNull(notification((int)task.id));
    }
    @Test public void delayedDuplicateWatchDoneDoesNotDuplicateRecurringTask() throws Exception {
        TaskStore.Task task=store.add("Recurring",1,System.currentTimeMillis()-1000,1,-1);TaskNotifications.show(context,task);PendingIntent callback=watchDone(notification((int)task.id)).actionIntent;send(callback);assertEquals(1,store.activeAll().size());long next=store.activeAll().get(0).id;send(callback);assertEquals(1,store.activeAll().size());assertEquals(next,store.activeAll().get(0).id);assertTrue(store.find(task.id).done);
    }
    @Test public void changingModeUpdatesExistingTaskAndDueCardsInPlace(){
        TaskStore.Task task=store.add("Mode change",1,System.currentTimeMillis()-1000,0,-1);TaskNotifications.show(context,task);TaskNotifications.showDue(context,task);TaskNotifications.setWatchEnabled(context,false);Notification phone=notification((int)task.id);assertTrue((phone.flags&Notification.FLAG_ONGOING_EVENT)!=0);assertTrue((phone.flags&Notification.FLAG_NO_CLEAR)!=0);assertTrue((phone.flags&Notification.FLAG_LOCAL_ONLY)!=0);assertTrue((notification(1000000000+(int)task.id).flags&Notification.FLAG_LOCAL_ONLY)!=0);TaskNotifications.setWatchEnabled(context,true);assertEquals(0,notification((int)task.id).flags&(Notification.FLAG_ONGOING_EVENT|Notification.FLAG_NO_CLEAR|Notification.FLAG_LOCAL_ONLY));assertEquals(0,notification(1000000000+(int)task.id).flags&Notification.FLAG_LOCAL_ONLY);assertEquals(2,manager.getActiveNotifications().length);assertFalse(store.find(task.id).done);
    }
    @Test public void swipingWatchCardDoesNotCompleteOrImmediatelyRepostIt() throws Exception {
        TaskStore.Task task=store.add("Swipe is not done");TaskNotifications.show(context,task);PendingIntent dismiss=notification((int)task.id).deleteIntent;manager.cancel((int)task.id);send(dismiss);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));assertFalse(store.find(task.id).done);assertNull(notification((int)task.id));
    }
    @Test public void futureAndSilentTasksDoNotAppearEarlyOrGainReminders(){
        TaskStore.Task future=store.add("Future",1,System.currentTimeMillis()+3600000,0,0),silent=store.add("Silent",1,System.currentTimeMillis()+3600000,0,-2);TaskNotifications.setWatchEnabled(context,true);assertNull(notification((int)future.id));assertNull(notification((int)silent.id));assertEquals(-2,store.find(silent.id).notifyBeforeMinutes);assertFalse(TaskNotifications.quickAddEnabled(context));
    }
}
