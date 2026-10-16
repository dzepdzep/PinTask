package com.pintask.app;

import android.content.Context;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FlexibleRepeatTest {
    Context context;TaskStore store;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear().putBoolean("custom_shifts_guide_seen_v28",true).putBoolean("interactive_shift_guide_v29",true).commit();store=new TaskStore(context);
    }
    @After public void close(){store.close();}
    @Test public void presetAndCustomIntervalsAdvanceByCalendarUnits() {
        int[] codes={1,2,3,4,5,6,7,8,9,1005,2005,3002};
        LocalDateTime first=LocalDate.now().plusYears(1).withDayOfMonth(10).atTime(9,15);
        for(int code:codes) {
            long due=first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            TaskStore.Task task=store.add("Repeat "+code,1,due,code,15);
            TaskStore.Task next=store.complete(task.id).nextTask;
            Calendar expected=Calendar.getInstance();expected.setTimeInMillis(due);expected.add(RepeatRules.field(code),RepeatRules.amount(code));
            assertEquals(expected.getTimeInMillis(),next.dueAt);assertEquals(code,next.recurrence);
        }
    }
    @Test public void everyOtherDayKeepsLocalTimeAcrossDst() {
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        try {
            LocalDate spring=LocalDate.of(LocalDate.now().getYear()+1,3,31);
            while(spring.getDayOfWeek()!=DayOfWeek.SUNDAY)spring=spring.minusDays(1);
            long due=spring.minusDays(1).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            TaskStore.Task next=store.complete(store.add("Every other day",1,due,5,0).id).nextTask;
            assertEquals(spring.plusDays(1).atTime(9,0),Instant.ofEpochMilli(next.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime());
            assertEquals(47*3600000L,next.dueAt-due);
        }finally{TimeZone.setDefault(previous);}
    }
    @Test public void skipPreservesTaskSettingsAndDoesNotCreateDoneHistory() {
        TaskGroups.select(context,"Home");long due=System.currentTimeMillis()+86400000;
        TaskStore.Task original=store.add("Sheets",2,due,2003,-2);
        store.snooze(original.id,due+60000);
        TaskStore.Task skipped=store.skipOccurrence(original.id);
        assertEquals(original.id,skipped.id);assertEquals("Home",skipped.group);assertEquals(2,skipped.priority);
        assertEquals(-2,skipped.notifyBeforeMinutes);assertEquals(0,skipped.snoozedUntil);assertEquals(0,skipped.remindedAt);
        assertFalse(skipped.done);assertEquals(0,store.completedPersonalCount());assertEquals(1,store.activePersonal().size());
        Calendar expected=Calendar.getInstance();expected.setTimeInMillis(due);expected.add(Calendar.WEEK_OF_YEAR,3);
        assertEquals(expected.getTimeInMillis(),skipped.dueAt);
        assertThrows(IllegalArgumentException.class,()->store.skipOccurrence(store.add("Not repeating").id));
    }
    @Test public void overdueSkipRetainsOriginalDayPattern() {
        Calendar old=Calendar.getInstance();old.add(Calendar.DAY_OF_MONTH,-9);old.set(Calendar.HOUR_OF_DAY,9);
        long start=old.getTimeInMillis();TaskStore.Task task=store.add("Late",1,start,5,0);
        TaskStore.Task next=store.skipOccurrence(task.id);
        Calendar expected=(Calendar)old.clone();do{expected.add(Calendar.DAY_OF_MONTH,2);}while(expected.getTimeInMillis()<=System.currentTimeMillis());
        assertEquals(expected.getTimeInMillis(),next.dueAt);assertEquals(0,store.completedPersonalCount());
    }
    @Test public void customRulesRoundTripAndInvalidBackupRollsBack() throws Exception {
        TaskStore.Task task=store.add("Custom",1,System.currentTimeMillis()+3600000,3002,0);
        String backup=BackupData.export(store);BackupData.restore(store,backup);assertEquals(3002,store.find(task.id).recurrence);
        JSONObject bad=new JSONObject(backup);bad.getJSONArray("tasks").getJSONObject(0).put("recurrence",2999);
        assertThrows(IllegalArgumentException.class,()->BackupData.restore(store,bad.toString()));assertEquals(3002,store.find(task.id).recurrence);
        for(int code:new int[]{-1,13,1000,1366,2053,3025})assertFalse(RepeatRules.valid(code));
        assertThrows(IllegalArgumentException.class,()->store.add("Invalid",1,System.currentTimeMillis()+60000,13,0));
    }
    @Test public void customRepeatFormSurvivesRotationAndSavesSelectedCode() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();find(root,"Add with time or repeat").performClick();
            root=controller.get().getWindow().getDecorView();first(root,EditText.class).setText("Custom draft");
            find(root,"More options").performClick();root=controller.get().getWindow().getDecorView();
            find(root,"Custom repeat…").performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            first(dialog.getWindow().getDecorView(),EditText.class).setText("5");
            first(dialog.getWindow().getDecorView(),Spinner.class).setSelection(1);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(dialog.isShowing());
            assertEquals("Before rotation",2005,RepeatRules.selected(repeatSpinner(controller.get().getWindow().getDecorView())));
            controller.recreate();
            root=controller.get().getWindow().getDecorView();assertEquals(2005,RepeatRules.selected(repeatSpinner(root)));
            assertEquals("Every 5 weeks",repeatSpinner(root).getSelectedItem());
            assertEquals("Custom draft",first(root,EditText.class).getText().toString());
        }
    }
    private Spinner repeatSpinner(View view) {
        if(view instanceof Spinner && view.getTag() instanceof Integer)return (Spinner)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Spinner found=repeatSpinner(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    private TextView find(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return (TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView r=find(((ViewGroup)v).getChildAt(i),text);if(r!=null)return r;}return null;
    }
    private <T> T first(View v,Class<T> type) {
        if(type.isInstance(v))return type.cast(v);
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){T r=first(((ViewGroup)v).getChildAt(i),type);if(r!=null)return r;}return null;
    }
}
