package com.pintask.app;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.app.*;
import android.view.*;
import android.widget.*;
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
public class AllQualityOfLifeTest {
    Context context;TaskStore store;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");context.getSharedPreferences("settings",0).edit().clear().putBoolean("custom_shifts_guide_seen_v28",true).putBoolean("interactive_shift_guide_v29",true).putBoolean("startup_access_reviewed_v31",true).commit();store=new TaskStore(context);}
    @After public void close(){store.close();}
    @Test public void notesAndChecksPersistAndNewRepeatResetsOnlyTicks() {
        TaskStore.Task task=store.add("Room",1,System.currentTimeMillis()+3600000,1,0);
        String steps=TaskDetails.fromLines("Sheets\nPillows","[]");JSONArray rows=TaskDetails.parse(steps);
        try{rows.getJSONObject(0).put("checked",true);}catch(JSONException e){throw new AssertionError(e);}
        store.saveDetails(task.id,"Use blue sheets",rows.toString(),false);
        store.close();store=new TaskStore(context);task=store.find(task.id);
        assertEquals("Use blue sheets",task.notes);assertTrue(TaskDetails.parse(task.checklist).optJSONObject(0).optBoolean("checked"));
        TaskStore.Task next=store.complete(task.id).nextTask;
        assertEquals(task.notes,next.notes);assertFalse(TaskDetails.parse(next.checklist).optJSONObject(0).optBoolean("checked"));
        assertTrue(TaskDetails.parse(store.find(task.id).checklist).optJSONObject(0).optBoolean("checked"));
        store.undoCompletion(task.id);assertTrue(TaskDetails.parse(store.find(task.id).checklist).optJSONObject(0).optBoolean("checked"));assertNull(store.find(next.id));
    }
    @Test public void completionUndoDoesNotDeleteEditedNextTask() {
        TaskStore.Task original=store.add("Room",1,System.currentTimeMillis()+3600000,1,0);
        TaskStore.Task next=store.complete(original.id).nextTask;store.saveDetails(next.id,"Edited", "[]",false);
        assertThrows(IllegalArgumentException.class,()->store.undoCompletion(original.id));assertNotNull(store.find(next.id));
    }
    @Test public void bundlesCreateIndependentUntimedCopiesInChosenGroup() {
        TaskStore.Task source=store.add("Prepare",2,System.currentTimeMillis()+60000,1,15);store.saveDetails(source.id,"Instructions",TaskDetails.fromLines("Check\nClean","[]"),false);
        long bundle=TaskBundles.save(store,"Room setup","Job A",TaskBundles.capture(Arrays.asList(store.find(source.id))));
        List<Long> first=TaskBundles.apply(store,bundle,"Patient B"),second=TaskBundles.apply(store,bundle,"Patient B");
        assertNotEquals(first.get(0),second.get(0));TaskStore.Task copy=store.find(first.get(0));assertEquals("Patient B",copy.group);assertEquals(0,copy.dueAt);assertEquals(0,copy.recurrence);assertEquals(-2,copy.notifyBeforeMinutes);assertEquals(2,copy.priority);assertEquals("Instructions",copy.notes);
        store.rename(copy.id,"Changed");assertEquals("Prepare",store.find(second.get(0)).title);assertEquals("Prepare",store.find(source.id).title);
        assertEquals("Job A",store.find(TaskBundles.apply(store,bundle,null).get(0)).group);
    }
    @Test public void pauseSuppressesClaimsCompletionAndAlarmSchedulingUntilResume() {
        TaskStore.Task task=store.add("Pause me",1,System.currentTimeMillis()-3600000,1,0);
        store.pause(task.id,true);assertNull(store.claimDueReminder(task.id));assertNull(store.complete(task.id));
        ReminderScheduler.schedule(context,store.find(task.id));assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().isEmpty());
        TaskStore.Task resumed=store.pause(task.id,false);assertFalse(resumed.paused);assertTrue(resumed.dueAt>System.currentTimeMillis());
        ReminderScheduler.schedule(context,resumed);assertEquals(1,Shadows.shadowOf(context.getSystemService(AlarmManager.class)).getScheduledAlarms().size());
        store.pause(task.id,true);store.stopRepeating(task.id);assertFalse(store.find(task.id).paused);assertFalse(store.find(task.id).repeatAfter);
    }
    @Test public void afterCompletionCountsFromFinishInsteadOfOriginalDate() {
        TaskStore.Task task=store.add("Water plants",1,System.currentTimeMillis()+86400000*5L,5,0);store.saveDetails(task.id,"", "[]",true);
        TaskStore.Task next=store.complete(task.id).nextTask;Calendar expected=Calendar.getInstance();expected.setTimeInMillis(store.find(task.id).completedAt);expected.add(Calendar.DAY_OF_MONTH,2);
        assertEquals(expected.getTimeInMillis(),next.dueAt);assertTrue(next.repeatAfter);assertTrue(next.dueAt<task.dueAt);
        store.undoCompletion(task.id);assertNull(store.find(next.id));assertFalse(store.find(task.id).done);
    }
    @Test public void selectedWeekdaysHandleFirstDateWeekendsAndNextMatchingDay() {
        LocalDate friday=LocalDate.now().plusYears(1);while(friday.getDayOfWeek()!=DayOfWeek.FRIDAY)friday=friday.plusDays(1);
        ZoneId zone=ZoneId.systemDefault();long due=friday.atTime(9,0).atZone(zone).toInstant().toEpochMilli();
        TaskStore.Task mwf=store.add("MWF",1,due,12,0);assertEquals(friday.plusDays(3).atTime(9,0),local(store.complete(mwf.id).nextTask.dueAt));
        TaskStore.Task weekend=store.add("Weekend",1,due,11,0);assertEquals(friday.plusDays(1).atTime(9,0),local(weekend.dueAt));
        TaskStore.Task selected=store.add("Tue Fri",1,due,4000+2+16,0);assertEquals(friday.plusDays(4).atTime(9,0),local(store.complete(selected.id).nextTask.dueAt));
        assertFalse(RepeatRules.valid(4000));assertFalse(RepeatRules.valid(4128));
    }
    @Test public void tomorrowCanBeUndoneWithoutLosingOriginalStateOrHistory() {
        TaskStore.Task original=store.add("Anytime",1,0,0,-2);TaskStore.Task moved=store.tomorrow(original.id);
        assertEquals(LocalDate.now().plusDays(1).atTime(9,0),local(moved.dueAt));assertEquals(-2,moved.notifyBeforeMinutes);
        store.undoTomorrow(original,moved.dueAt);assertEquals(0,store.find(original.id).dueAt);assertEquals(0,store.completedPersonalCount());
        TaskStore.Task again=store.tomorrow(original.id);store.updateTiming(original.id,again.dueAt+60000,0,-2);
        assertThrows(IllegalArgumentException.class,()->store.undoTomorrow(original,again.dueAt));
    }
    @Test public void movingWeeklyTaskToTomorrowDoesNotShiftFutureRepeatPattern() {
        Calendar originalDate=Calendar.getInstance();originalDate.add(Calendar.DAY_OF_MONTH,3);originalDate.set(Calendar.HOUR_OF_DAY,9);
        long original=originalDate.getTimeInMillis();TaskStore.Task task=store.add("Weekly",1,original,2,0);
        TaskStore.Task moved=store.tomorrow(task.id);assertEquals(original,moved.repeatAnchor);
        TaskStore.Task next=store.complete(task.id).nextTask;originalDate.add(Calendar.WEEK_OF_YEAR,1);assertEquals(originalDate.getTimeInMillis(),next.dueAt);
    }
    @Test public void globalSearchFindsOtherGroupNotesWithoutChangingSelectedGroup() {
        TaskGroups.select(context,"Patient B");TaskStore.Task b=store.add("Bed");store.saveDetails(b.id,"Blue sheets",TaskDetails.fromLines("Pillows","[]"),false);
        TaskGroups.select(context,"Job A");store.add("Desk");
        assertTrue(TaskFilters.scoped(context,store.activeAll(),"blue",0,false,System.currentTimeMillis()).isEmpty());
        assertEquals(b.id,TaskFilters.scoped(context,store.activeAll(),"blue",0,true,System.currentTimeMillis()).get(0).id);
        assertEquals("Job A",TaskGroups.selected(context));assertEquals(b.id,TaskFilters.scoped(context,store.activeAll(),"pillows",0,true,System.currentTimeMillis()).get(0).id);
    }
    @Test public void backupIncludesBundlesNotesTicksAndPauseAndImportsOlderFormat() throws Exception {
        TaskStore.Task task=store.add("Saved",1,System.currentTimeMillis()+3600000,10,-2);store.saveDetails(task.id,"Memo",TaskDetails.fromLines("Step","[]"),true);store.pause(task.id,true);
        long bundle=TaskBundles.save(store,"Bundle","Home",TaskBundles.capture(Arrays.asList(store.find(task.id))));
        String backup=BackupData.export(store);BackupData.restore(store,backup);assertTrue(store.find(task.id).paused);assertTrue(store.find(task.id).repeatAfter);assertEquals("Memo",store.find(task.id).notes);assertEquals(1,TaskBundles.apply(store,bundle,"Home").size());
        JSONObject old=new JSONObject(backup);old.remove("bundles");JSONArray tasks=old.getJSONArray("tasks");for(int i=0;i<tasks.length();i++)for(String field:new String[]{"notes","checklist","paused","repeat_after"})tasks.getJSONObject(i).remove(field);
        BackupData.restore(store,old.toString());assertEquals("",store.find(task.id).notes);assertEquals("[]",store.find(task.id).checklist);assertFalse(store.find(task.id).paused);
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM bundles",null)){c.moveToFirst();assertEquals(0,c.getInt(0));}
    }
    @Test public void malformedChecklistOrBundleImportLeavesExistingDataUntouched() throws Exception {
        TaskStore.Task task=store.add("Keep");String original=BackupData.export(store);JSONObject bad=new JSONObject(original);bad.getJSONArray("tasks").getJSONObject(0).put("checklist","[{\"text\":\"Step\",\"checked\":\"yes\"}]");
        assertThrows(IllegalArgumentException.class,()->BackupData.restore(store,bad.toString()));assertEquals("Keep",store.find(task.id).title);
        assertThrows(IllegalArgumentException.class,()->TaskBundles.save(store,"Bad","","[]"));
    }
    @Test public void detailsEditorAndCheckboxAreUsableInTheApp() {
        TaskStore.Task task=store.add("Details");
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            TaskDetails.edit(controller.get(),task,()->{});Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            List<EditText> fields=new ArrayList<>();entries(dialog.getWindow().getDecorView(),fields);fields.get(0).setText("Remember this");fields.get(1).setText("First\nSecond");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("Remember this",store.find(task.id).notes);
            TaskDetails.open(controller.get(),store.find(task.id),()->{});Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();check(dialog.getWindow().getDecorView()).performClick();assertTrue(TaskDetails.parse(store.find(task.id).checklist).optJSONObject(0).optBoolean("checked"));
        }
    }
    @Test public void versionSixUpgradePreservesIdsAndAddsEmptyExtras() {
        store.close();context.deleteDatabase("pintask.db");SQLiteDatabase db=context.openOrCreateDatabase("pintask.db",0,null);
        db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,created_at INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,completed_at INTEGER NOT NULL DEFAULT 0,priority INTEGER NOT NULL DEFAULT 1,due_at INTEGER NOT NULL DEFAULT 0,recurrence INTEGER NOT NULL DEFAULT 0,reminded_at INTEGER NOT NULL DEFAULT 0,notify_before_min INTEGER NOT NULL DEFAULT -1,shift_run_id INTEGER NOT NULL DEFAULT 0,shift_label TEXT NOT NULL DEFAULT '',snoozed_until INTEGER NOT NULL DEFAULT 0,next_task_id INTEGER NOT NULL DEFAULT 0,task_group TEXT NOT NULL DEFAULT '')");
        ShiftRepository.createTables(db);db.execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,priority INTEGER NOT NULL DEFAULT 1)");long id=TaskStore.insert(db,"Keep v6",1,2,1000,5,15,0,"");db.setVersion(6);db.close();store=new TaskStore(context);
        TaskStore.Task task=store.find(id);assertEquals(id,task.id);assertEquals(5,task.recurrence);assertEquals(15,task.notifyBeforeMinutes);assertEquals("",task.notes);assertEquals("[]",task.checklist);assertFalse(task.paused);assertFalse(task.repeatAfter);assertEquals(8,store.getReadableDatabase().getVersion());
    }
    private LocalDateTime local(long value){return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDateTime();}
    private void entries(View view,List<EditText> result){if(view instanceof EditText)result.add((EditText)view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)entries(((ViewGroup)view).getChildAt(i),result);}
    private CheckBox check(View view){if(view instanceof CheckBox)return (CheckBox)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){CheckBox result=check(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
}
