package com.pintask.app;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CustomShiftSetupTest {
    Context context;TaskStore store;ShiftRepository repository;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().commit();
        store=new TaskStore(context);repository=new ShiftRepository(context);
    }
    @After public void tearDown(){repository.close();store.close();}

    @Test public void customHoursAndOvernightTasksAreIndependentOfFixedPresets(){
        LocalDate date=LocalDate.now().plusDays(4);
        ShiftRepository.Template custom=repository.addTemplate("My late shift",19*60+30,7*60+15);
        ShiftRepository.Run run=repository.scheduleCustom(date,custom.id,false);
        assertEquals("My late shift",run.label);
        assertEquals(custom.id,run.templateId);
        assertEquals(date.atTime(19,30),Instant.ofEpochMilli(run.startsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        assertEquals(date.plusDays(1).atTime(7,15),Instant.ofEpochMilli(run.endsAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
        assertTrue(repository.runTasks(run.id).isEmpty());
        repository.addTask(run.id,"Medication check",22*60,0,0);
        repository.addTask(run.id,"After midnight check",2*60,0,0);
        assertEquals(2,repository.runTasks(run.id).size());
        assertEquals(run.id,repository.scheduleCustom(date,custom.id,false).id);
    }

    @Test public void customMultiDateConflictRollsBackAndPreservesLegacyRuns(){
        LocalDate day=LocalDate.now().plusDays(5);
        ShiftRepository.Template custom=repository.addTemplate("Early",7*60,15*60);
        ShiftRepository.Run original=repository.schedule(day.plusDays(1),"TD1");
        assertThrows(IllegalArgumentException.class,()->repository.scheduleCustomDates(Arrays.asList(day,day.plusDays(1)),custom.id,false));
        assertTrue(repository.runsForDate(day).isEmpty());
        assertEquals(original.id,repository.runsForDate(day.plusDays(1)).get(0).id);
        repository.scheduleCustomDates(Arrays.asList(day,day.plusDays(1)),custom.id,true);
        assertEquals(1,repository.runsForDate(day).size());
        assertEquals(2,repository.runsForDate(day.plusDays(1)).size());
        assertEquals("TD1",repository.findRun(original.id).label);
    }

    @Test public void firstRunGuideCanBeDismissedAndReplayed(){
        try(var controller=Robolectric.buildActivity(ShiftActivity.class).setup()){
            assertTrue(context.getSharedPreferences("settings",0).getBoolean("custom_shifts_guide_seen_v28",false));
            assertEquals(ShiftGuide.TITLES.length,ShiftGuide.DETAILS.length);
            assertTrue(ShiftGuide.DETAILS[3].contains("Add task to this shift"));
            View root=controller.get().getWindow().getDecorView();
            find(root,"More").performClick();
            assertNotNull(find(controller.get().getWindow().getDecorView(),"Shift setup guide  ›"));
            find(controller.get().getWindow().getDecorView(),"Shift setup guide  ›").performClick();
            assertEquals("Welcome to PinTask",org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getTitle());
        }
    }

    private TextView find(View root,String text){
        if(root instanceof TextView && text.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            TextView found=find(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
}
