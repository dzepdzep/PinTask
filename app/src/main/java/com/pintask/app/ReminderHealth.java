package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** In-app checks that identify when Android can silence or delay task alerts. */
final class ReminderHealth {
    private ReminderHealth() {}

    static String status(Activity a) {
        TaskNotifications.ensureChannel(a);
        boolean notifications=TaskNotifications.canNotify(a);
        boolean channel=TaskNotifications.canNotifyChannel(a,TaskNotifications.activeReminderChannel(a));
        boolean audible=TaskNotifications.canAlertAudibly(a,TaskNotifications.activeReminderChannel(a));
        boolean exact=ReminderScheduler.canScheduleExact(a);
        AudioManager audio=a.getSystemService(AudioManager.class);
        int level=audio==null?-1:audio.getStreamVolume(AudioManager.STREAM_ALARM);
        return "Notification permission: "+(notifications?"Allowed":"BLOCKED")+
                "\nTimed alarm category: "+(channel?"Enabled":"BLOCKED")+
                "\nAudible alarm channel: "+(audible?"Ready":"SILENT/LOW IMPORTANCE — check category sound")+
                "\nExact alarm permission: "+(exact?"Enabled":"NOT ALLOWED — alerts can be late")+
                "\nAlarm volume: "+(level<0?"Unknown":level==0?"MUTED":"Level "+level)+
                "\n\nAndroid Do Not Disturb, silent modes and the phone maker's battery controls may still restrict sounds. " +
                "This check cannot guarantee future delivery. Test with the screen locked.";
    }

    static void show(Activity a) {
        String[] choices={
            "Play an alarm notification now",
            "Schedule an alarm test in 60 seconds",
            "Enable alarms for existing silent timed tasks…",
            "Open exact alarm permission",
            "Open notification settings",
            "Open app battery/settings"
        };
        new AlertDialog.Builder(a).setTitle("PinTask · Reminder health check")
                .setMessage(status(a))
                .setItems(choices,(d,which)->{
                    switch(which) {
                        case 0: immediateTest(a);break;
                        case 1: delayedTest(a);break;
                        case 2: offerExisting(a);break;
                        case 3: openExact(a);break;
                        case 4: openNotifications(a);break;
                        case 5: openAppSettings(a);break;
                    }
                }).setPositiveButton("Close",null).show();
    }

    static void warnWhenBlocked(Activity a) {
        if(!TaskNotifications.canNotify(a) ||
                !TaskNotifications.canAlertAudibly(a,TaskNotifications.activeReminderChannel(a))) {
            new AlertDialog.Builder(a).setTitle("Your task alarm may be silent")
                    .setMessage("Android notifications may be blocked, or the active Timed task alarms category may be silent or low importance. The task is saved, but it may not make a sound until you fix its notification category settings.")
                    .setNegativeButton("Later",null).setPositiveButton("Fix notifications",(d,i)->openNotifications(a)).show();
        } else if(!ReminderScheduler.canScheduleExact(a)) {
            new AlertDialog.Builder(a).setTitle("Your timed reminder could arrive late")
                    .setMessage("Exact alarm access is disabled. PinTask saved this task with an inexact fallback, which Android may delay. Allow Alarms & reminders for the intended time.")
                    .setNegativeButton("Later",null).setPositiveButton("Enable exact alarms",(d,i)->openExact(a)).show();
        }
    }

    private static void immediateTest(Activity a) {
        if(!TaskNotifications.canAlertAudibly(a,TaskNotifications.activeReminderChannel(a))) {
            warnWhenBlocked(a);return;
        }
        try(TaskStore store=new TaskStore(a)) {
            TaskStore.Task test=store.add("PIN TASK TEST · Tap Done",1,
                    System.currentTimeMillis()-2000,TaskStore.REPEAT_NEVER,0);
            ReminderScheduler.deliver(a,test.id);
        }
        Toast.makeText(a,"Test posted. Check whether your alarm ringtone and vibration worked.",Toast.LENGTH_LONG).show();
    }

    private static void delayedTest(Activity a) {
        if(!TaskNotifications.canAlertAudibly(a,TaskNotifications.activeReminderChannel(a))) {
            warnWhenBlocked(a);return;
        }
        try(TaskStore store=new TaskStore(a)) {
            TaskStore.Task test=store.add("PIN TASK 60-SECOND ALARM TEST",1,
                    System.currentTimeMillis()+60_000,TaskStore.REPEAT_NEVER,0);
            ReminderScheduler.schedule(a,test);
        }
        new AlertDialog.Builder(a).setTitle("Test scheduled")
                .setMessage("Lock the phone. An audible test notification should arrive about 60 seconds from now. If exact alarm permission is missing, Android can delay it. The test task will remain until you tap Done.\n\n"+status(a))
                .setPositiveButton("OK",null).show();
    }

    private static void offerExisting(Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("Enable old silent timed reminders?")
                .setMessage("This makes all FUTURE unfinished, non-paused tasks that currently have No reminder alert at their task time. It does not alter task times, completed items, or existing alert choices. Tasks intentionally set to No reminder will also be changed.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Enable",(d,i)->{
                    int count=enableExistingTimed(a);
                    Toast.makeText(a,"Enabled alarms for "+count+" timed tasks",Toast.LENGTH_LONG).show();
                    warnWhenBlocked(a);
                }).show();
    }

    static int enableExistingTimed(android.content.Context a) {
        int count;
        try(TaskStore store=new TaskStore(a)) {
            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();
            android.content.ContentValues values=new android.content.ContentValues();
            values.put("notify_before_min",0);
            values.put("reminded_at",0);
            count=db.update("tasks",values,
                "done=0 AND paused=0 AND snoozed_until=0 AND due_at>? AND notify_before_min=?",
                new String[]{Long.toString(System.currentTimeMillis()),
                    Integer.toString(TaskStore.REMINDER_OFF)});
        }
        ReminderScheduler.rescheduleAll(a);
        return count;
    }

    private static void openExact(Activity a) {
        if(Build.VERSION.SDK_INT<31){Toast.makeText(a,"Exact alarm permission is not required on this Android version",Toast.LENGTH_LONG).show();return;}
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())));
        } catch(Exception unavailable) {openAppSettings(a);}
    }

    private static void openNotifications(Activity a) {
        try {
            Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName());
            a.startActivity(i);
        }catch(Exception unavailable){openAppSettings(a);}
    }

    private static void openAppSettings(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+a.getPackageName())));
    }
}
