package com.pintask.app;

import android.content.Context;
import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ShiftFlowTest {
    Context context;TaskStore store;ShiftRepository shifts;LocalDate day;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");context.getSharedPreferences("settings",0).edit().clear().commit();store=new TaskStore(context);shifts=new ShiftRepository(context);day=LocalDate.now().plusDays(5);}
    @After public void close(){shifts.close();store.close();}
    @Test public void dayAndNightCoexistWithIndependentTasksAndIdempotentScheduling(){
        ShiftRepository.Run td=shifts.schedule(day,"TD1");shifts.addTask(td.id,"Day rounds",600,0,15);
        ShiftRepository.Run nd=shifts.schedule(day,"ND");shifts.addTask(nd.id,"Night rounds",60,0,-2);
        assertEquals(2,shifts.runsForDate(day).size());assertEquals(td.endsAt,nd.startsAt);
        assertEquals(td.id,shifts.schedule(day,"TD1").id);assertEquals(nd.id,shifts.schedule(day,"ND").id);
        assertEquals("Day rounds",shifts.runTasks(td.id).get(0).title);
        assertEquals(day.plusDays(1).atTime(1,0),Instant.ofEpochMilli(shifts.runTasks(nd.id).get(0).dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        shifts.endShift(td.id);assertEquals(1,shifts.runTasks(nd.id).size());assertNotNull(shifts.findRun(nd.id));
    }
    @Test public void td2HasCorrectHoursAndOverlapRequiresExplicitChoice(){
        ShiftRepository.Run td=shifts.schedule(day,"TD2");assertEquals(day.atTime(9,0),Instant.ofEpochMilli(td.startsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());assertEquals(day.atTime(21,0),Instant.ofEpochMilli(td.endsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        assertThrows(IllegalArgumentException.class,()->shifts.schedule(day,"ND"));assertEquals(1,shifts.runsForDate(day).size());shifts.schedule(day,"ND",true);assertEquals(2,shifts.runsForDate(day).size());
    }
    @Test public void noShiftLabelsCannotReceiveTasksOrEraseWorkingShifts(){
        ShiftRepository.Run u8=shifts.schedule(day,"U8");assertTrue(u8.dayLabel);assertThrows(IllegalArgumentException.class,()->shifts.addTask(u8.id,"Invalid",-1,0,-2));assertThrows(IllegalArgumentException.class,()->shifts.saveRunAsTemplate(u8.id,"Invalid"));
        ShiftRepository.Run ez=shifts.schedule(day,"EZ");assertTrue(ez.dayLabel);assertEquals(1,shifts.runsForDate(day).size());
        ShiftRepository.Run td=shifts.schedule(day,"TD1");assertFalse(td.dayLabel);assertEquals(1,shifts.runsForDate(day).size());assertThrows(IllegalArgumentException.class,()->shifts.schedule(day,"Off"));assertNotNull(shifts.findRun(td.id));
    }
    @Test public void templateSnapshotsAllOccurrencesDetailsAndSilentReminders() throws Exception {
        TaskGroups.select(context,"Patient A");ShiftRepository.Run nd=shifts.schedule(day,"ND");shifts.addTask(nd.id,"Rounds",60,120,-2);shifts.addTask(nd.id,"Handover",-1,0,-2);
        TaskStore.Task source=shifts.runTasks(nd.id).get(1);store.saveDetails(source.id,"Check notes","[{\"text\":\"Supplies\",\"checked\":true}]",false);store.complete(source.id);
        ShiftRepository.Template template=shifts.saveRunAsTemplate(nd.id,"Night routine");assertEquals(5,shifts.steps(template.id).size());assertTrue(store.find(source.id).done);assertTrue(store.find(source.id).checklist.contains("true"));
        BackupData.restore(store,BackupData.export(store));
        TaskGroups.select(context,"Patient B");ShiftRepository.Run target=shifts.schedule(day.plusDays(1),"ND");shifts.applyTemplate(target.id,template.id);
        List<TaskStore.Task> copies=shifts.runTasks(target.id);assertEquals(5,copies.size());boolean notes=false;
        for(TaskStore.Task copy:copies){assertFalse(copy.done);assertEquals(-2,copy.notifyBeforeMinutes);assertEquals("Patient B",copy.group);assertEquals(target.id,copy.shiftRunId);if(!copy.notes.isEmpty()){notes=true;assertEquals("Check notes",copy.notes);assertTrue(copy.checklist.contains("false"));}if(copy.dueAt>0)assertTrue(copy.dueAt>=target.startsAt && copy.dueAt<target.endsAt);}
        assertTrue(notes);ShiftRepository.Template duplicate=shifts.duplicateTemplate(template.id,"Copy");assertEquals("Check notes",shifts.steps(duplicate.id).get(1).notes);
    }
    @Test public void incompatibleTemplateRollsBackWithoutPartialTasks(){
        ShiftRepository.Run td=shifts.schedule(day,"TD1");shifts.addTask(td.id,"Rounds",600,0,15);ShiftRepository.Template template=shifts.saveRunAsTemplate(td.id,"Day");ShiftRepository.Run nd=shifts.schedule(day,"ND");assertThrows(IllegalArgumentException.class,()->shifts.applyTemplate(nd.id,template.id));assertTrue(shifts.runTasks(nd.id).isEmpty());
    }
    @Test public void oldBackupDefaultsKeepLegacyU8AsAWorkingShift() throws Exception {
        ShiftRepository.Run run=shifts.schedule(day,"TD1");shifts.addTask(run.id,"Keep",-1,0,-2);
        JSONObject backup=new JSONObject(BackupData.export(store));JSONObject legacy=backup.getJSONArray("shift_runs").getJSONObject(0);legacy.remove("day_label");legacy.put("label","U8");legacy.put("ends_at",ShiftTimes.atMinuteOffset(run.startsAt,480));
        BackupData.restore(store,backup.toString());assertFalse(shifts.findRun(run.id).dayLabel);assertEquals("Keep",shifts.runTasks(run.id).get(0).title);
    }
    @Test public void versionSevenMigrationPreservesLegacyRunsAndTaskDetails(){
        ShiftRepository.Run run=shifts.schedule(day,"TD1");shifts.addTask(run.id,"Keep",-1,0,-2);ShiftRepository.Template template=shifts.saveRunAsTemplate(run.id,"Keep template");
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();
        db.execSQL("CREATE TABLE old_runs AS SELECT _id,template_id,label,started_at,ends_at,ended,task_group FROM shift_runs");db.execSQL("DROP TABLE shift_runs");db.execSQL("ALTER TABLE old_runs RENAME TO shift_runs");db.execSQL("UPDATE shift_runs SET label='U8'");
        db.execSQL("CREATE TABLE old_steps AS SELECT _id,template_id,title,first_min,every_min,priority,notify_before_min FROM shift_steps");db.execSQL("DROP TABLE shift_steps");db.execSQL("ALTER TABLE old_steps RENAME TO shift_steps");db.setVersion(7);shifts.close();store.close();store=new TaskStore(context);shifts=new ShiftRepository(context);
        assertEquals(8,store.getReadableDatabase().getVersion());assertFalse(shifts.findRun(run.id).dayLabel);assertEquals("Keep",shifts.runTasks(run.id).get(0).title);assertEquals("",shifts.steps(template.id).get(0).notes);
    }
    @Test public void dateIsVisibleAndEditableBesideTimeWithoutMoreOptions(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            find(controller.get().getWindow().getDecorView(),"Add with time or repeat").performClick();View root=controller.get().getWindow().getDecorView();assertTrue(find(root,"Change date").isShown());assertNotNull(find(root,"Date: "+LocalDate.now()));find(root,"Time · Optional").performClick();TimePickerDialog picker=(TimePickerDialog)ShadowAlertDialog.getLatestAlertDialog();picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();root=controller.get().getWindow().getDecorView();assertTrue(find(root,"Change date").isShown());assertTrue(find(root,"Date: "+LocalDate.now()).isShown());
        }
    }
    @Test public void shiftPageIsSpecificAndSaveTemplateRemainsAtBottomAfterAddingAndRotation(){
        ShiftRepository.Run td=shifts.schedule(day,"TD1"),nd=shifts.schedule(day,"ND");shifts.addTask(td.id,"Day item",-1,0,-2);shifts.addTask(nd.id,"Night item",-1,0,-2);
        android.content.Intent intent=TaskSections.openIntent(context,shifts.runTasks(td.id).get(0).id);
        try(var controller=Robolectric.buildActivity(ShiftActivity.class,intent).setup()){
            View root=controller.get().getWindow().getDecorView();description(root,"TD1 · 08:00 – 20:00").performClick();root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Day item"));assertNull(find(root,"Night item"));find(root,"Add task to this shift").performClick();root=controller.get().getWindow().getDecorView();entry(root).setText("Second day item");find(root,"Save task").performClick();controller.recreate();root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Second day item"));assertNull(find(root,"Night item"));find(root,"Save as template").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(1,shifts.templates().size());assertEquals(2,shifts.steps(shifts.templates().get(0).id).size());
        }
    }
    private View description(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=description(((ViewGroup)view).getChildAt(i),label);if(result!=null)return result;}return null;}
    private EditText entry(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText result=entry(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
    private TextView find(View view,String label){if(view instanceof TextView && label.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=find(((ViewGroup)view).getChildAt(i),label);if(result!=null)return result;}return null;}
}
