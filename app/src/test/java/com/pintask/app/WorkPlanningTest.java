package com.pintask.app;

import android.app.*;
import android.content.Context;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WorkPlanningTest {
    Context context;TaskStore store;ShiftRepository shifts;LocalDate day;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");context.getSharedPreferences("settings",0).edit().clear().commit();store=new TaskStore(context);shifts=new ShiftRepository(context);day=LocalDate.now().plusDays(5);}
    @After public void close(){shifts.close();store.close();}
    @Test public void batchSchedulesUniqueDatesAndNeverDuplicatesExistingShift(){
        List<LocalDate> dates=Arrays.asList(day.plusDays(2),day,day);assertEquals(2,shifts.scheduleDates(dates,"TD1",false).size());shifts.scheduleDates(dates,"TD1",false);assertEquals(1,shifts.runsForDate(day).size());assertEquals(1,shifts.runsForDate(day.plusDays(2)).size());
        assertThrows(IllegalArgumentException.class,()->shifts.scheduleDates(Collections.emptyList(),"TD1",false));assertThrows(IllegalArgumentException.class,()->shifts.scheduleDates(Arrays.asList(LocalDate.now().minusDays(1)),"TD1",false));
    }
    @Test public void lateBatchConflictRollsBackEveryDateUntilOverlapIsConfirmed(){
        ShiftRepository.Run night=shifts.schedule(day.plusDays(1),"ND");List<LocalDate> dates=Arrays.asList(day,day.plusDays(1));assertThrows(IllegalArgumentException.class,()->shifts.scheduleDates(dates,"TD2",false));assertTrue(shifts.runsForDate(day).isEmpty());assertEquals(night.id,shifts.runsForDate(day.plusDays(1)).get(0).id);shifts.scheduleDates(dates,"TD2",true);assertEquals(1,shifts.runsForDate(day).size());assertEquals(2,shifts.runsForDate(day.plusDays(1)).size());
    }
    @Test public void copyWholeDayRetainsGroupsTimesDetailsAndResetsCompletion(){
        TaskGroups.select(context,"Patient A");ShiftRepository.Run td=shifts.schedule(day,"TD1"),nd=shifts.schedule(day,"ND");shifts.addTask(td.id,"Day item",600,0,15);shifts.addTask(nd.id,"Night item",60,0,-2);TaskStore.Task original=shifts.runTasks(nd.id).get(0);store.saveDetails(original.id,"Notes","[{\"text\":\"Supplies\",\"checked\":true}]",false);store.complete(original.id);
        List<ShiftRepository.Run> copies=shifts.copyDay(day,Arrays.asList(day.plusDays(2)),false);assertEquals(2,copies.size());assertTrue(store.find(original.id).done);assertTrue(store.find(original.id).checklist.contains("true"));TaskStore.Task copied=shifts.runTasks(copies.get(1).id).get(0);assertFalse(copied.done);assertEquals(-2,copied.notifyBeforeMinutes);assertEquals("Notes",copied.notes);assertTrue(copied.checklist.contains("false"));assertEquals("Patient A",copied.group);assertEquals(day.plusDays(3).atTime(1,0),Instant.ofEpochMilli(copied.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime());assertEquals(15,shifts.runTasks(copies.get(0).id).get(0).notifyBeforeMinutes);
    }
    @Test public void copyIsAtomicAndRefusesOccupiedDaysOrSourceDate(){
        ShiftRepository.Run source=shifts.schedule(day,"TD1");shifts.addTask(source.id,"Keep",-1,0,-2);shifts.schedule(day.plusDays(3),"TD1");assertThrows(IllegalArgumentException.class,()->shifts.copyDay(day,Arrays.asList(day.plusDays(2),day.plusDays(3)),false));assertTrue(shifts.runsForDate(day.plusDays(2)).isEmpty());assertEquals(1,store.activeAll().size());assertEquals(1,shifts.runTasks(source.id).size());assertThrows(IllegalArgumentException.class,()->shifts.copyDay(day,Arrays.asList(day),false));
    }
    @Test public void copyingSelectedGroupLeavesOtherJobsUntouchedAndCopiesOffDays(){
        TaskGroups.select(context,"A");ShiftRepository.Run a=shifts.schedule(day,"TD1");shifts.addTask(a.id,"A task",-1,0,-2);TaskGroups.select(context,"B");ShiftRepository.Run b=shifts.schedule(day,"ND");shifts.addTask(b.id,"B task",-1,0,-2);TaskGroups.select(context,"A");List<ShiftRepository.Run> copied=shifts.copyDay(day,Arrays.asList(day.plusDays(2)),false);assertEquals(1,copied.size());assertEquals("A task",shifts.runTasks(copied.get(0).id).get(0).title);TaskGroups.select(context,"B");assertTrue(shifts.runsForDate(day.plusDays(2)).isEmpty());shifts.schedule(day.plusDays(4),"Off");assertTrue(shifts.copyDay(day.plusDays(4),Arrays.asList(day.plusDays(5)),false).get(0).dayLabel);
    }
    @Test public void nextShiftUsesEarliestFutureWorkingShiftInSelectedJob(){
        assertNull(shifts.nextRun());TaskGroups.select(context,"A");ShiftRepository.Run a=shifts.schedule(day,"TD1");shifts.schedule(day.minusDays(1),"Off");TaskGroups.select(context,"B");ShiftRepository.Run b=shifts.schedule(day.minusDays(1),"ND");assertEquals(b.id,shifts.nextRun().id);TaskGroups.select(context,"A");assertEquals(a.id,shifts.nextRun().id);shifts.endShift(a.id);assertNull(shifts.nextRun());TaskGroups.select(context,null);assertEquals(b.id,shifts.nextRun().id);
    }
    @Test public void copyPreservesSourceOverlapAndDstWallClockTimes(){
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));try{
            shifts.schedule(day,"TD2");ShiftRepository.Run nd=shifts.schedule(day,"ND",true);shifts.addTask(nd.id,"Rounds",60,0,5);LocalDate target=LocalDate.of(LocalDate.now().getYear()+1,10,31);while(target.getDayOfWeek()!=DayOfWeek.SATURDAY)target=target.minusDays(1);
            List<ShiftRepository.Run> copied=shifts.copyDay(day,Arrays.asList(target),false);assertEquals(2,copied.size());ShiftRepository.Run night=copied.get(1);assertEquals(target.plusDays(1).atTime(8,0),Instant.ofEpochMilli(night.endsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());assertEquals(target.plusDays(1).atTime(1,0),Instant.ofEpochMilli(shifts.runTasks(night.id).get(0).dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        }finally{TimeZone.setDefault(previous);}
    }
    @Test public void retiredLabelsAreAbsentFromPickerAndPlanningDatesSurviveRotation(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();find(root,"Work").performClick();find(controller.get().getWindow().getDecorView(),"Set a shift").performClick();idle();DatePickerDialog picker=(DatePickerDialog)ShadowAlertDialog.getLatestAlertDialog();picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();AlertDialog choice=ShadowAlertDialog.getLatestAlertDialog();assertEquals(4,choice.getListView().getAdapter().getCount());for(int i=0;i<4;i++){String text=choice.getListView().getAdapter().getItem(i).toString();assertFalse(text.contains("U8"));assertFalse(text.contains("EZ"));}choice.dismiss();
            find(controller.get().getWindow().getDecorView(),"Copy day / plan several days").performClick();idle();ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null,1,1);idle();find(controller.get().getWindow().getDecorView(),"Add date").performClick();idle();picker=(DatePickerDialog)ShadowAlertDialog.getLatestAlertDialog();picker.updateDate(day.getYear(),day.getMonthValue()-1,day.getDayOfMonth());picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();controller.recreate();root=controller.get().getWindow().getDecorView();find(root,"Apply to 1 day").performClick();assertEquals(1,shifts.runsForDate(day).size());assertEquals("TD1",shifts.runsForDate(day).get(0).label);
        }
    }
    @Test public void nextShiftShortcutOpensItsTaskList(){
        ShiftRepository.Run run=shifts.schedule(day,"ND");shifts.addTask(run.id,"Next night task",-1,0,-2);try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){find(controller.get().getWindow().getDecorView(),"Work").performClick();find(controller.get().getWindow().getDecorView(),"Next shift").performClick();View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Next night task"));assertNotNull(find(root,"Save as template"));}
    }
    private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private TextView find(View view,String label){if(view instanceof TextView && label.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=find(((ViewGroup)view).getChildAt(i),label);if(result!=null)return result;}return null;}
}
