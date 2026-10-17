package com.pintask.app;

import android.app.NotificationManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RoutineSchedulingTest {
    Context context;TaskStore store;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().commit();
        store=new TaskStore(context);
        context.getSystemService(NotificationManager.class).cancelAll();
    }
    @After public void close(){store.close();}

    @Test public void dailyMultipleTimesCreateIndependentValidSchedules() {
        ZoneId zone=ZoneId.of("Europe/Berlin");
        long now=LocalDate.of(2026,10,17).atTime(11,0).atZone(zone).toInstant().toEpochMilli();
        List<Long> due=RoutineDialog.firstOccurrences(true,Arrays.asList(9*60,14*60,20*60),1,now,zone);
        assertEquals(3,due.size());
        assertEquals(LocalDate.of(2026,10,18).atTime(9,0),
                Instant.ofEpochMilli(due.get(0)).atZone(zone).toLocalDateTime());
        assertEquals(LocalDate.of(2026,10,17).atTime(14,0),
                Instant.ofEpochMilli(due.get(1)).atZone(zone).toLocalDateTime());
        assertEquals(LocalDate.of(2026,10,17).atTime(20,0),
                Instant.ofEpochMilli(due.get(2)).atZone(zone).toLocalDateTime());
        assertEquals(3,new HashSet<>(due).size());
    }

    @Test public void hourlyIntervalKeepsRepeatingFromWallClockTime(){
        assertTrue(RepeatRules.valid(5001));assertTrue(RepeatRules.valid(5024));
        assertEquals("Every hour",RepeatRules.label(5001));
        assertEquals("Every 3 hours",RepeatRules.label(5003));
        assertEquals(0,RepeatRules.days(5001));
        long now=System.currentTimeMillis();
        long first=RoutineDialog.firstOccurrences(false,Collections.emptyList(),2,now,ZoneId.systemDefault()).get(0);
        assertEquals(2*3600000L,first-now);
        assertEquals(first+2*3600000L,RepeatRules.next(first,5002,first));
    }

    @Test public void automaticRoutineSchedulesNextWhenAlertFiresWithoutCompletingPrior(){
        TaskStore.Task task=store.add("Hourly check",1,System.currentTimeMillis()-2000,5001,0);
        task=store.enableAutomaticRoutine(task.id);
        assertTrue(task.autoRepeat);
        ReminderScheduler.deliver(context,task.id);
        List<TaskStore.Task> after=store.activePersonal();
        assertEquals(2,after.size());
        TaskStore.Task original=store.find(task.id);
        assertFalse(original.done);
        assertTrue(original.remindedAt>0);
        TaskStore.Task next=after.get(0).id==task.id?after.get(1):after.get(0);
        assertTrue(next.autoRepeat);
        assertEquals(task.id,next.routineRoot);
        assertEquals(5001,next.recurrence);
        assertTrue(next.dueAt>System.currentTimeMillis());
        ReminderScheduler.deliver(context,task.id);
        assertEquals(2,store.activePersonal().size());
        TaskStore.Completion completion=store.complete(task.id);
        assertEquals(next.id,completion.nextTask.id);
        assertEquals(1,store.activePersonal().size());
        assertFalse(store.find(next.id).done);
    }

    @Test public void existingRepeatsAreNotChangedToAutomatic(){
        TaskStore.Task regular=store.add("Legacy daily",1,System.currentTimeMillis()-2000,1,0);
        ReminderScheduler.deliver(context,regular.id);
        assertEquals(1,store.activePersonal().size());
        assertFalse(store.find(regular.id).autoRepeat);
        store.complete(regular.id);
        assertEquals(1,store.activePersonal().size());
    }

    @Test public void stoppingOneRoutineDeletesOnlyItsFutureUnalertedOccurrences(){
        TaskStore.Task a=store.enableAutomaticRoutine(store.add("Pills",1,System.currentTimeMillis()-1000,5001,0).id);
        TaskStore.Task b=store.enableAutomaticRoutine(store.add("Pills",1,System.currentTimeMillis()+3600000,5001,0).id);
        ReminderScheduler.deliver(context,a.id);
        TaskStore.Task upcoming=null;
        for(TaskStore.Task candidate:store.activePersonal())
            if(candidate.routineRoot==a.id&&candidate.id!=a.id)upcoming=candidate;
        assertNotNull(upcoming);
        List<Long> cancelled=store.stopAutomaticRoutine(a.id);
        assertTrue(cancelled.contains(upcoming.id));
        assertNull(store.find(upcoming.id));
        assertEquals(0,store.find(a.id).recurrence);
        assertFalse(store.find(a.id).autoRepeat);
        assertTrue(store.find(b.id).autoRepeat);
    }

    @Test public void backupRestoresOldFormatWithoutRoutineColumns() throws Exception {
        TaskStore.Task task=store.add("Old task",1,System.currentTimeMillis()+600000,1,0);
        JSONObject saved=new JSONObject(BackupData.export(store));
        JSONArray rows=saved.getJSONArray("tasks");
        rows.getJSONObject(0).remove("auto_repeat");
        rows.getJSONObject(0).remove("routine_root");
        BackupData.restore(store,saved.toString());
        assertFalse(store.find(task.id).autoRepeat);
        assertEquals(0,store.find(task.id).routineRoot);
    }
}
