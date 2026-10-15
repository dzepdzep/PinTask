package com.pintask.app;

import android.content.*;
import android.app.NotificationManager;
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
public class GroupsAndRepeatsTest {
    Context context; TaskStore store; ShiftRepository shifts;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication(); context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().commit();
        store=new TaskStore(context);shifts=new ShiftRepository(context);
    }
    @After public void close() {shifts.close();store.close();}
    @Test public void persistentQuickAddIsOffByDefaultAndToggleRemovesItImmediately() {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        TaskStore.Task task=store.add("Keep reminder");TaskNotifications.show(context,task);
        TaskNotifications.syncAll(context);
        assertNull(Shadows.shadowOf(manager).getNotification(1999999999));
        TaskNotifications.setQuickAddEnabled(context,true);
        assertNotNull(Shadows.shadowOf(manager).getNotification(1999999999));
        TaskNotifications.setQuickAddEnabled(context,false);
        new BootReceiver().onReceive(context,new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(Shadows.shadowOf(manager).getNotification(1999999999));
        assertNotNull(Shadows.shadowOf(manager).getNotification((int)task.id));
    }
    @Test public void jobGroupsSeparateTasksAndSchedulesButNeverSuppressReminderDelivery() {
        TaskGroups.select(context,"Job A");
        TaskStore.Task a=store.add("A",1,System.currentTimeMillis()-1000,0,0);
        ShiftRepository.Run shiftA=shifts.schedule(LocalDate.now().plusDays(2),"TD");shifts.addTask(shiftA.id,"A rounds",-1,0,-2);
        TaskGroups.select(context,"Patient B"); TaskStore.Task b=store.add("B");
        ShiftRepository.Run shiftB=shifts.schedule(LocalDate.now().plusDays(2),"ND");
        assertEquals("Patient B",shifts.findRun(shiftB.id).group);
        assertEquals(1,shifts.runsForDate(LocalDate.now().plusDays(2)).size());
        assertEquals(b.id,TaskGroups.filter(context,store.activePersonal()).get(0).id);
        ReminderScheduler.deliver(context,a.id);
        assertNotNull(Shadows.shadowOf(context.getSystemService(NotificationManager.class)).getNotification((int)a.id));
        TaskGroups.select(context,null);
        assertEquals(2,shifts.runsForDate(LocalDate.now().plusDays(2)).size());
        assertEquals(2,store.activePersonal().size());
        assertEquals("Job A",shifts.runTasks(shiftA.id).get(0).group);
    }
    @Test public void everyOtherFridayKeepsWeekdayTimeAndGroupAcrossDst() {
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        try {
            LocalDate friday=LocalDate.of(LocalDate.now().getYear()+1,3,1);
            while(friday.getDayOfWeek()!=DayOfWeek.FRIDAY) friday=friday.plusDays(1);
            TaskGroups.select(context,"Home");
            long due=friday.atTime(9,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            TaskStore.Task task=store.add("Change sheets",1,due,TaskStore.REPEAT_FORTNIGHTLY,0);
            TaskGroups.select(context,"Work");
            for(int i=0;i<4;i++) {
                task=store.complete(task.id).nextTask;
                LocalDateTime next=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime();
                friday=friday.plusDays(14);assertEquals(friday.atTime(9,30),next);assertEquals("Home",task.group);
            }
        } finally {TimeZone.setDefault(previous);}
    }
    @Test public void groupedBackupRoundTripsAndOldBackupsDefaultToUngrouped() throws Exception {
        TaskGroups.select(context,"Home");TaskStore.Task task=store.add("Sheets",1,System.currentTimeMillis()+3600000,4,0);
        shifts.schedule(LocalDate.now().plusDays(2),"U8");String backup=BackupData.export(store);
        BackupData.restore(store,backup);assertEquals("Home",store.find(task.id).group);assertEquals(4,store.find(task.id).recurrence);
        JSONObject old=new JSONObject(backup);
        for(String table:new String[]{"tasks","shift_runs"}) {JSONArray rows=old.getJSONArray(table);for(int i=0;i<rows.length();i++)rows.getJSONObject(i).remove("task_group");}
        BackupData.restore(store,old.toString());assertEquals("",store.find(task.id).group);
    }
    @Test public void versionFiveAddsGroupsWithoutChangingTaskOrShiftIds() {
        store.close();shifts.close();context.deleteDatabase("pintask.db");
        android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase("pintask.db",0,null);
        db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,created_at INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,completed_at INTEGER NOT NULL DEFAULT 0,priority INTEGER NOT NULL DEFAULT 1,due_at INTEGER NOT NULL DEFAULT 0,recurrence INTEGER NOT NULL DEFAULT 0,reminded_at INTEGER NOT NULL DEFAULT 0,notify_before_min INTEGER NOT NULL DEFAULT -1,shift_run_id INTEGER NOT NULL DEFAULT 0,shift_label TEXT NOT NULL DEFAULT '',snoozed_until INTEGER NOT NULL DEFAULT 0,next_task_id INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE shift_runs (_id INTEGER PRIMARY KEY AUTOINCREMENT,template_id INTEGER NOT NULL,label TEXT NOT NULL,started_at INTEGER NOT NULL,ends_at INTEGER NOT NULL,ended INTEGER NOT NULL DEFAULT 0)");
        ShiftRepository.createTables(db);
        db.execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,priority INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("INSERT INTO shift_runs VALUES (7,0,'ND',1000,2000,0)");
        long id=TaskStore.insert(db,"Keep shift task",1,2,1500,0,15,7,"ND");
        db.setVersion(5);db.close();store=new TaskStore(context);shifts=new ShiftRepository(context);
        assertEquals(id,store.find(id).id);assertEquals(7,store.find(id).shiftRunId);
        assertEquals(15,store.find(id).notifyBeforeMinutes);assertEquals("",store.find(id).group);
        assertEquals(7,shifts.findRun(7).id);assertEquals("",shifts.findRun(7).group);
    }

    @Test public void dayShiftUsesEightOClockOnDstChangeDay() {
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        try {
            LocalDate day=LocalDate.of(LocalDate.now().getYear()+1,3,31);
            while(day.getDayOfWeek()!=DayOfWeek.SUNDAY)day=day.minusDays(1);
            ShiftRepository.Run run=shifts.schedule(day,"TD");
            assertEquals(day.atTime(8,0),Instant.ofEpochMilli(run.startsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
            assertEquals(day.atTime(20,0),Instant.ofEpochMilli(run.endsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        }finally{TimeZone.setDefault(previous);}
    }
}
