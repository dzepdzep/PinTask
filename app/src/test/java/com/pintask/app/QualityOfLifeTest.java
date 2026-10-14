package com.pintask.app;

import android.content.Context;
import android.content.Intent;
import android.app.Notification;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.util.Calendar;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class QualityOfLifeTest {
    private Context context;
    private TaskStore store;
    private ShiftRepository shifts;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication(); context.deleteDatabase("pintask.db");
        store=new TaskStore(context); shifts=new ShiftRepository(context);
    }
    @After public void cleanup() { shifts.close(); store.close(); }

    @Test public void snoozePreservesDueAndRecurrenceAndDefersUntimedNotifications() {
        long due=System.currentTimeMillis()+3_600_000;
        TaskStore.Task task=store.add("Repeat",1,due,TaskStore.REPEAT_DAILY,15);
        long snooze=due+900_000;
        TaskStore.Task updated=store.snooze(task.id,snooze);
        assertEquals(due,updated.dueAt); assertEquals(TaskStore.REPEAT_DAILY,updated.recurrence);
        assertEquals(snooze,updated.notificationAt()); assertNull(store.claimDueReminder(task.id));
        TaskStore.Task plain=store.add("Whenever");
        TaskNotifications.ensureChannel(context);
        TaskNotifications.show(context,plain);
        TaskNotifications.cancel(context,plain.id);
        TaskNotifications.show(context,store.snooze(plain.id,snooze));
        assertNull(Shadows.shadowOf(context.getSystemService(NotificationManager.class)).getNotification((int)plain.id));
        store.updateTiming(task.id,due,0,0);
        assertEquals(0,store.find(task.id).snoozedUntil);
    }

    @Test public void favoritesAddFreshUntimedTasksAndRemoveWithoutDeletingTasks() {
        TaskStore.Task original=store.add("Reusable",2,System.currentTimeMillis()+60_000,1,0);
        long favorite=store.saveFavorite(original.id);
        assertEquals(favorite,store.saveFavorite(original.id));
        TaskStore.Task first=store.addFavorite(favorite),second=store.addFavorite(favorite);
        assertNotEquals(first.id,second.id); assertEquals(0,first.dueAt); assertEquals(0,first.recurrence); assertEquals(2,first.priority);
        store.removeFavorite(favorite); assertTrue(store.favorites().isEmpty()); assertNotNull(store.find(first.id));
    }

    @Test public void duplicateShiftHasIndependentRulesAndCreatesNoRun() {
        ShiftRepository.Template original=shifts.addTemplate("Night",1200,480);
        shifts.addStep(original.id,"Anytime",-1,0,1,0);
        shifts.addStep(original.id,"Hourly",0,60,2,5);
        ShiftRepository.Template copy=shifts.duplicateTemplate(original.id,"Other shift");
        assertNotEquals(original.id,copy.id); assertEquals(2,shifts.steps(copy.id).size()); assertNull(shifts.currentRun());
        shifts.deleteStep(shifts.steps(copy.id).get(0).id,copy.id);
        shifts.updateTemplateTimes(copy.id,600,1080);
        assertEquals(2,shifts.steps(original.id).size()); assertEquals(1200,shifts.findTemplate(original.id).startMinute);
    }

    @Test public void quickUndoRemovesNextOccurrenceButRejectsChangedNextTask() {
        TaskStore.Task task=store.add("Daily",1,System.currentTimeMillis()+60_000,1,0);
        long next=store.complete(task.id).nextTask.id;
        assertEquals(next,store.undoCompletion(task.id)); assertNull(store.find(next)); assertFalse(store.find(task.id).done);
        next=store.complete(task.id).nextTask.id;
        store.rename(next,"Edited next task");
        assertThrows(IllegalArgumentException.class,()->store.undoCompletion(task.id));
        assertNotNull(store.find(next)); assertTrue(store.find(task.id).done);
    }

    @Test public void quickUndoExpiresAndDoesNotReopenEndedShift() {
        TaskStore.Task task=store.add("Expired"); store.complete(task.id);
        store.getWritableDatabase().execSQL("UPDATE tasks SET completed_at=completed_at-11000 WHERE _id="+task.id);
        assertThrows(IllegalArgumentException.class,()->store.undoCompletion(task.id));
        ShiftRepository.Template t=shifts.addTemplate("Shift",600,1080); shifts.addStep(t.id,"Task",-1,0,1,0);
        ShiftRepository.Run run=shifts.startShift(t.id,System.currentTimeMillis()-60_000);
        long id=shifts.runTasks(run.id).get(0).id; store.complete(id); shifts.endShift(run.id);
        assertThrows(IllegalArgumentException.class,()->store.undoCompletion(id));
    }

    @Test public void backupRoundTripIncludesHistoryFavoritesAndShiftRules() throws Exception {
        TaskStore.Task plain=store.add("Reusable"); store.saveFavorite(plain.id); store.complete(plain.id);
        ShiftRepository.Template t=shifts.addTemplate("Night",1200,480); shifts.addStep(t.id,"Anytime",-1,0,1,0);
        shifts.startShift(t.id,System.currentTimeMillis()+60_000);
        String backup=BackupData.export(store);
        store.add("Not in backup");
        BackupData.restore(store,backup);
        assertEquals(1,store.completedPersonalCount()); assertEquals(1,store.favorites().size());
        assertEquals(1,shifts.templates().size()); assertEquals(1,store.active().size());
        assertEquals(-1,shifts.steps(t.id).get(0).firstMinute);
    }

    @Test public void invalidBackupRollsBackAllData() throws Exception {
        TaskStore.Task task=store.add("Keep me");
        ShiftRepository.Template t=shifts.addTemplate("Shift",600,1080); shifts.addStep(t.id,"Task",-1,0,1,0);
        JSONObject root=new JSONObject(BackupData.export(store));
        root.getJSONArray("shift_steps").getJSONObject(0).put("template_id",999);
        assertThrows(IllegalArgumentException.class,()->BackupData.restore(store,root.toString()));
        assertEquals("Keep me",store.find(task.id).title); assertEquals(1,shifts.templates().size());
        root.put("version",99);
        assertThrows(IllegalArgumentException.class,()->BackupData.restore(store,root.toString()));
        assertEquals(1,store.activePersonal().size());
    }

    @Test public void filtersMatchTextDateAndPriority() {
        long now=System.currentTimeMillis();
        store.add("Buy MILK",2,0,0,-1);
        store.add("Overdue",1,now-60_000,0,0);
        Calendar today=Calendar.getInstance(); today.setTimeInMillis(now);
        store.add("Today",0,now,0,0);
        assertEquals(1,TaskFilters.apply(store.activePersonal(),"milk",0,now).size());
        assertEquals(1,TaskFilters.apply(store.activePersonal(),"",1,now).size());
        assertEquals(1,TaskFilters.apply(store.activePersonal(),"",3,now).size());
        assertEquals(1,TaskFilters.apply(store.activePersonal(),"",4,now).size());
        assertTrue(TaskFilters.apply(store.activePersonal(),"missing",0,now).isEmpty());
    }

    @Test public void widgetRendersTasksAndReceiverCompletesAndUndoes() {
        TaskStore.Task task=store.add("Widget task");
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int widgetId=Shadows.shadowOf(manager).createWidget(TaskWidget.class,R.layout.task_widget);
        TextView title=Shadows.shadowOf(manager).getViewFor(widgetId).findViewById(R.id.widget_task_title);
        assertEquals("Widget task",title.getText().toString());
        new TaskActionReceiver().onReceive(context,new Intent(TaskNotifications.ACTION_DONE).putExtra(TaskNotifications.EXTRA_ID,task.id));
        assertTrue(store.find(task.id).done);
        new TaskActionReceiver().onReceive(context,new Intent(TaskNotifications.ACTION_UNDO).putExtra(TaskNotifications.EXTRA_ID,task.id));
        assertFalse(store.find(task.id).done);
        Notification notification=Shadows.shadowOf(context.getSystemService(NotificationManager.class)).getNotification((int)task.id);
        assertEquals("Snooze",notification.actions[1].title.toString());
    }
}
