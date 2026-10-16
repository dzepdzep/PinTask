package com.pintask.app;

import android.content.*;
import android.app.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SimplerHomeTest {
    Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("pintask.db");context.getSharedPreferences("settings",0).edit().clear().commit();}
    @Test public void oldEnabledSettingAndTaggedShortcutAreClearedWithoutRemovingTasks() {
        NotificationManager manager=context.getSystemService(NotificationManager.class);TaskNotifications.ensureChannel(context);
        context.getSharedPreferences("settings",0).edit().putBoolean("quick_add",true).commit();
        Notification.Action action=new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context,R.drawable.ic_check),"Add",null).addRemoteInput(new RemoteInput.Builder(TaskNotifications.REMOTE_TEXT).setLabel("New task").build()).build();
        manager.notify("old-shortcut",678,new Notification.Builder(context,TaskNotifications.CHANNEL).setSmallIcon(R.drawable.ic_pin).setContentTitle("Old Quick Add").addAction(action).build());
        manager.notify("legacy",1999999999,new Notification.Builder(context,TaskNotifications.CHANNEL).setSmallIcon(R.drawable.ic_pin).setContentTitle("PinTask Quick Add").build());
        TaskStore.Task task;try(TaskStore store=new TaskStore(context)){task=store.add("PinTask · Quick Add");}TaskNotifications.show(context,task);
        new BootReceiver().onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        assertFalse(TaskNotifications.quickAddEnabled(context));
        assertEquals(1,manager.getActiveNotifications().length);assertEquals(task.id,manager.getActiveNotifications()[0].getId());
        new BootReceiver().onReceive(context,new Intent(Intent.ACTION_BOOT_COMPLETED));assertEquals(1,manager.getActiveNotifications().length);
        TaskNotifications.setQuickAddEnabled(context,true);assertEquals(2,manager.getActiveNotifications().length);
        TaskNotifications.setQuickAddEnabled(context,false);assertEquals(1,manager.getActiveNotifications().length);
    }
    @Test public void launcherOpensTodayAndAdvancedControlsStayOutOfBasicTaskForm() {
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());assertNotNull(launch);assertEquals(ShiftActivity.class.getName(),launch.getComponent().getClassName());
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"More"));assertNull(find(root,"Task bundles"));assertNull(find(root,"Optional templates"));
            find(root,"Add with time or repeat").performClick();root=controller.get().getWindow().getDecorView();
            assertEquals(View.GONE,find(root,"Reminder · Optional").getParent() instanceof View?((View)find(root,"Reminder · Optional").getParent()).getVisibility():-1);
            assertFalse(find(root,"Custom repeat…").isShown());
            firstEntry(root).setText("Keep draft");find(root,"More options").performClick();root=controller.get().getWindow().getDecorView();assertEquals(View.VISIBLE,((View)find(root,"Reminder · Optional").getParent()).getVisibility());
            controller.recreate();root=controller.get().getWindow().getDecorView();assertEquals("Keep draft",firstEntry(root).getText().toString());assertNotNull(find(root,"Less options"));
        }
    }
    @Test public void calendarShiftPickerCreatesPlanWithoutShowingTemplateEditorOrTaskForm() {
        try(org.robolectric.android.controller.ActivityController<ShiftActivity> controller=Robolectric.buildActivity(ShiftActivity.class).setup()) {
            find(controller.get().getWindow().getDecorView(),"Work").performClick();
            try(ShiftRepository custom=new ShiftRepository(context)){custom.addTemplate("Night Custom",1200,480);}
            find(controller.get().getWindow().getDecorView(),"Set a shift").performClick();View root=controller.get().getWindow().getDecorView();assertNull(find(root,"Optional templates"));assertNull(find(root,"End / clear TD"));
            android.app.DatePickerDialog datePicker=(android.app.DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();datePicker.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("Night Custom · 20:00–08:00 (+1 day)",dialog.getListView().getAdapter().getItem(0));
            assertEquals(3,dialog.getListView().getAdapter().getCount());
            dialog.getListView().performItemClick(dialog.getListView().getChildAt(0),0,0);
            try(ShiftRepository shifts=new ShiftRepository(context)){
                assertEquals("Night Custom",shifts.runsForDate(java.time.LocalDate.now()).get(0).label);
                assertEquals(1,shifts.templates().size());
            }
        }
    }
    @Test public void oldPinnedLauncherAlsoOpensTheSimpleHome() {
        Intent legacy=new Intent(context,MainActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,legacy).setup()) {
            assertEquals(ShiftActivity.class.getName(),Shadows.shadowOf(controller.get()).getNextStartedActivity().getComponent().getClassName());assertTrue(controller.get().isFinishing());
        }
    }
    private TextView find(View view,String text){if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=find(((ViewGroup)view).getChildAt(i),text);if(result!=null)return result;}return null;}
    private EditText firstEntry(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText result=firstEntry(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
}
