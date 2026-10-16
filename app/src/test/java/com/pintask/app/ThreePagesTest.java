package com.pintask.app;

import android.content.Context;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ThreePagesTest {
    Context context;TaskStore store;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");context.getSharedPreferences("settings",0).edit().clear().putBoolean("custom_shifts_guide_seen_v28",true).putBoolean("interactive_shift_guide_v29",true).putBoolean("startup_access_reviewed_v31",true).commit();store=new TaskStore(context);}
    @After public void close(){store.close();}
    @Test public void taskWorkAndAdvancedFeaturesHaveSeparateDestinations() {
        store.add("Personal groceries");TaskGroups.select(context,"Patient A");store.add("Patient notes");TaskGroups.select(context,null);
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Personal groceries"));assertNull(find(root,"Patient notes"));assertNull(find(root,"Set a shift"));assertNull(find(root,"Task bundles"));assertTrue(tab(root,"Tasks").isSelected());
            find(root,"Work").performClick();root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Patient notes"));assertNull(find(root,"Personal groceries"));assertNotNull(find(root,"Set a shift"));assertTrue(tab(root,"Work").isSelected());
            find(root,"More").performClick();root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Task library & search  ›"));assertNotNull(find(root,"Task bundles  ›"));assertNotNull(find(root,"Quick Add notification  ›"));assertNull(firstEntry(root));assertTrue(tab(root,"More").isSelected());
        }
    }
    @Test public void quickPersonalTaskIgnoresSelectedWorkGroupAndSurvivesRotation() {
        TaskGroups.select(context,"Job B");
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();firstEntry(root).setText("Buy milk");controller.recreate();root=controller.get().getWindow().getDecorView();assertEquals("Buy milk",firstEntry(root).getText().toString());find(root,"Add").performClick();
            assertEquals("",store.activeAll().get(0).group);assertEquals(0,store.activeAll().get(0).shiftRunId);assertEquals(-1,store.activeAll().get(0).notifyBeforeMinutes);assertEquals("Job B",TaskGroups.selected(context));assertNotNull(find(controller.get().getWindow().getDecorView(),"Buy milk"));
        }
    }
    @Test public void threeQuickTasksEachAppearImmediatelyAsIndependentNotifications() {
        android.app.NotificationManager notifications=context.getSystemService(android.app.NotificationManager.class);
        notifications.cancelAll();
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            for(String taskName:new String[]{"Check equipment","Prepare supplies","Document care"}) {
                View root=controller.get().getWindow().getDecorView();
                firstEntry(root).setText(taskName);
                find(root,"Add").performClick();
            }
            assertEquals(3,store.activeAll().size());
            for(TaskStore.Task task:store.activeAll()) {
                assertTrue(task.reminderEnabled());
                assertEquals(-1,task.notifyBeforeMinutes);
                assertNotNull("Missing notification for "+task.title,
                        Shadows.shadowOf(notifications).getNotification((int)task.id));
            }
        }
    }
    @Test public void workTaskWithoutShiftGetsAWorkGroupRatherThanLeakingOntoTasks() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();find(root,"Work").performClick();root=controller.get().getWindow().getDecorView();find(root,"Add work task").performClick();root=controller.get().getWindow().getDecorView();firstEntry(root).setText("Work paperwork");controller.recreate();find(controller.get().getWindow().getDecorView(),"Save task").performClick();
            assertEquals("Work",store.activeAll().get(0).group);root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Work paperwork"));find(root,"Tasks").performClick();assertNull(find(controller.get().getWindow().getDecorView(),"Work paperwork"));
        }
    }
    @Test public void shiftTasksStayInWorkAndDoNotDisappearWhenPersonalTasksAreAdded() {
        try(ShiftRepository shifts=new ShiftRepository(context)) {
            ShiftRepository.Run run=shifts.schedule(java.time.LocalDate.now().plusDays(2),"ND");shifts.addTask(run.id,"Night rounds",-1,0,-2);
        }
        TaskSections.addPersonal(store,"Personal task");assertEquals(1,TaskSections.personal(store.activeAll()).size());assertEquals(1,TaskSections.work(context,store.activeAll()).size());
    }
    @Test public void movingInlineTextIntoTimedEditorDoesNotLeaveADuplicateDraft() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();firstEntry(root).setText("One task");find(root,"Add with time or repeat").performClick();root=controller.get().getWindow().getDecorView();assertEquals("One task",firstEntry(root).getText().toString());find(root,"Save task").performClick();root=controller.get().getWindow().getDecorView();assertEquals("",firstEntry(root).getText().toString());assertEquals(1,store.activeAll().size());
        }
    }
    @Test public void unassignedWorkContextStillShowsNewJobTasks() {
        TaskGroups.select(context,"");
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();find(root,"Work").performClick();find(controller.get().getWindow().getDecorView(),"Add work task").performClick();root=controller.get().getWindow().getDecorView();firstEntry(root).setText("Visible work task");find(root,"Save task").performClick();assertEquals("Work",TaskGroups.selected(context));assertNotNull(find(controller.get().getWindow().getDecorView(),"Visible work task"));
        }
    }
    @Test public void personalTaskDateDoesNotChangeTheWorkPlanningDate() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();find(root,"Add with time or repeat").performClick();root=controller.get().getWindow().getDecorView();find(root,"More options").performClick();root=controller.get().getWindow().getDecorView();find(root,"Change date").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.app.DatePickerDialog personal=(android.app.DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();java.time.LocalDate future=java.time.LocalDate.now().plusDays(12);personal.updateDate(future.getYear(),future.getMonthValue()-1,future.getDayOfMonth());personal.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Date: "+future));tab(root,"Work").performClick();root=controller.get().getWindow().getDecorView();find(root,"Set a shift").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();android.app.DatePickerDialog work=(android.app.DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();java.time.LocalDate today=java.time.LocalDate.now();assertEquals(today.getYear(),work.getDatePicker().getYear());assertEquals(today.getMonthValue()-1,work.getDatePicker().getMonth());assertEquals(today.getDayOfMonth(),work.getDatePicker().getDayOfMonth());
        }
    }
    @Test public void keyboardDoneSavesWorkEditorWithoutDependingOnVisibleSaveButton() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();tab(root,"Work").performClick();find(controller.get().getWindow().getDecorView(),"Add work task").performClick();root=controller.get().getWindow().getDecorView();EditText name=firstEntry(root);name.setText("Keyboard task");name.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);assertEquals("Keyboard task",store.activeAll().get(0).title);assertNotNull(find(controller.get().getWindow().getDecorView(),"Keyboard task"));
        }
    }
    @Test public void workNotificationOpensMatchingWorkGroupInsteadOfPersonalTasks() {
        TaskGroups.select(context,"Patient A");TaskStore.Task work=store.add("Patient reminder");TaskGroups.select(context,"Job B");TaskNotifications.show(context,work);
        android.app.Notification notification=Shadows.shadowOf(context.getSystemService(android.app.NotificationManager.class)).getNotification((int)work.id);android.content.Intent intent=Shadows.shadowOf(notification.contentIntent).getSavedIntent();assertEquals(work.id,intent.getLongExtra("open_task",0));
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class,intent).setup()) {
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Patient reminder"));assertTrue(tab(root,"Work").isSelected());assertEquals("Patient A",TaskGroups.selected(context));
        }
    }
    @Test public void notificationRoutingKeepsAnUnfinishedPersonalEntry() {
        TaskGroups.select(context,"Work");TaskStore.Task work=store.add("Work reminder");
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();firstEntry(root).setText("Draft shopping");controller.newIntent(TaskSections.openIntent(context,work.id));root=controller.get().getWindow().getDecorView();assertTrue(tab(root,"Work").isSelected());tab(root,"Tasks").performClick();assertEquals("Draft shopping",firstEntry(controller.get().getWindow().getDecorView()).getText().toString());
        }
    }
    @Test public void simpleInlineAddRefreshesTheExistingHomeScreenWidget() {
        android.appwidget.AppWidgetManager manager=android.appwidget.AppWidgetManager.getInstance(context);int widget=Shadows.shadowOf(manager).createWidget(TaskWidget.class,R.layout.task_widget);
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();firstEntry(root).setText("New widget task");find(root,"Add").performClick();TextView title=Shadows.shadowOf(manager).getViewFor(widget).findViewById(R.id.widget_task_title);assertNotNull(title);assertEquals("New widget task",title.getText().toString());
        }
    }
    private View tab(View view,String label){if((label+" page").contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=tab(((ViewGroup)view).getChildAt(i),label);if(result!=null)return result;}return null;}
    private TextView find(View view,String text){if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=find(((ViewGroup)view).getChildAt(i),text);if(result!=null)return result;}return null;}
    private EditText firstEntry(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText result=firstEntry(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
}
