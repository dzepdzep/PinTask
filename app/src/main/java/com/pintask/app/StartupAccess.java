package com.pintask.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Audits on launch and whenever the user returns from a relevant settings screen.
 * Android special access must be approved in system Settings; an app cannot grant it.
 * Battery optimization and manufacturer auto-start controls are advisory, not mandatory permissions.
 */
final class StartupAccess {
    static final String REVIEWED = "startup_access_reviewed_v31";
    static final int REQUEST_NOTIFICATIONS = 81;
    static final int READY = 0, RUNTIME_NOTIFICATIONS = 1, APP_NOTIFICATIONS = 2,
            ALARM_CHANNEL = 3, TASK_CHANNEL = 4, EXACT_ALARMS = 5;

    private final ShiftActivity activity;
    private Runnable afterStartup;
    private boolean completedStartup;
    private boolean showing;
    private boolean awaitingSettings;
    private boolean awaitingRuntime;

    StartupAccess(ShiftActivity activity) { this.activity = activity; }

    /** Core access gates for sound and precise task reminders, recomputed every time. */
    static int nextRequired(Context context) {
        TaskNotifications.ensureChannel(context);
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return RUNTIME_NOTIFICATIONS;
        if (!TaskNotifications.canNotify(context)) return APP_NOTIFICATIONS;
        if (!TaskNotifications.canNotifyChannel(context, TaskNotifications.activeReminderChannel(context))) return ALARM_CHANNEL;
        if (!TaskNotifications.canNotifyChannel(context, TaskNotifications.CHANNEL)) return TASK_CHANNEL;
        if (!ReminderScheduler.canScheduleExact(context)) return EXACT_ALARMS;
        return READY;
    }

    static boolean batteryUnrestricted(Context context) {
        PowerManager power=context.getSystemService(PowerManager.class);
        return power!=null && power.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    static String summary(Context context) {
        TaskNotifications.ensureChannel(context);
        boolean runtime=Build.VERSION.SDK_INT<33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        NotificationManager nm=context.getSystemService(NotificationManager.class);
        boolean appAlerts=nm!=null && nm.areNotificationsEnabled();
        boolean alarms=TaskNotifications.canNotifyChannel(context,TaskNotifications.activeReminderChannel(context));
        boolean tasks=TaskNotifications.canNotifyChannel(context,TaskNotifications.CHANNEL);
        boolean exact=ReminderScheduler.canScheduleExact(context);
        AudioManager audio=context.getSystemService(AudioManager.class);
        int volume=audio==null?-1:audio.getStreamVolume(AudioManager.STREAM_ALARM);
        return "Notification permission: "+ok(runtime)+
                "\nApp notifications: "+ok(appAlerts)+
                "\nTimed task alarms: "+ok(alarms)+
                "\nUnfinished task cards: "+ok(tasks)+
                "\nExact alarm access: "+ok(exact)+
                "\nAlarm volume: "+(volume<0?"Unknown":volume==0?"Muted — turn up alarm volume":"On (level "+volume+")")+
                "\nBattery restrictions: "+(batteryUnrestricted(context)?"Unrestricted":"Optimization enabled · review recommended")+
                "\nManufacturer auto-start/background: Check manually if your phone offers these settings"+
                "\n\nPermissions allow alerts but do not guarantee a sound during Do Not Disturb or precise delivery under all power modes.";
    }

    private static String ok(boolean value) { return value?"Allowed":"Needs attention"; }

    /** Called once for each fresh launch; no modal when all core access is already granted. */
    void begin(Runnable completed) {
        afterStartup=completed;
        boolean reviewed=activity.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean(REVIEWED,false);
        activity.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putBoolean(REVIEWED,true).apply();
        if(reviewed && nextRequired(activity)==READY){finishStartup();return;}
        activity.getWindow().getDecorView().postDelayed(()->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            if(!reviewed || nextRequired(activity)!=READY) show(false);
            else finishStartup();
        },400);
    }

    /** Also available from More, even when the startup audit has been dismissed. */
    void review() { show(true); }

    void onResume() {
        if(!awaitingSettings)return;
        awaitingSettings=false;
        activity.getWindow().getDecorView().postDelayed(()->{
            if(!activity.isFinishing()&&!activity.isDestroyed()) show(true);
        },260);
    }

    void onPermissionResult(int request) {
        if(request!=REQUEST_NOTIFICATIONS)return;
        awaitingRuntime=false;
        activity.getWindow().getDecorView().post(()->{
            if(!activity.isFinishing()&&!activity.isDestroyed())show(true);
        });
    }

    private void show(boolean userInitiated) {
        if(showing || activity.isFinishing() || activity.isDestroyed())return;
        showing=true;
        int missing=nextRequired(activity);
        String description=(missing==READY?
                "All essential reminder permissions are currently allowed. "+
                "You can still review battery restrictions and test a locked-phone alarm.":
                "PinTask needs the missing access below before timed alarms can work reliably. "+
                "Tap Fix next to open Android's permission prompt or the correct settings page.")+
                "\n\n"+summary(activity);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle(missing==READY?"PinTask · Alarm access ready":"PinTask · Reminder access needed")
                .setMessage(description)
                .setPositiveButton(missing==READY?"Continue":"Fix next",(d,w)->{
                    showing=false;
                    if(missing==READY)finishStartup();
                    else request(missing);
                })
                .setNeutralButton("Battery settings",(d,w)->{
                    showing=false;openBatterySettings();
                })
                .setNegativeButton("Later",(d,w)->{
                    showing=false;finishStartup();
                })
                .create();
        dialog.setOnCancelListener(d->{showing=false;finishStartup();});
        dialog.show();
    }

    private void request(int missing) {
        switch(missing) {
            case RUNTIME_NOTIFICATIONS:
                awaitingRuntime=true;
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);
                break;
            case APP_NOTIFICATIONS:
            case ALARM_CHANNEL:
            case TASK_CHANNEL:
            case EXACT_ALARMS: launch(settingsForIssue(activity,missing));break;
            default: finishStartup();
        }
    }

    private void launch(Intent intent) {
        try {
            awaitingSettings=true;
            activity.startActivity(intent);
        } catch (Exception unsupported) {
            awaitingSettings=false;
            try {
                awaitingSettings=true;
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:"+activity.getPackageName())));
            } catch (Exception alsoUnavailable) {
                awaitingSettings=false;
                android.widget.Toast.makeText(activity,
                        "Open Android Settings → Apps → PinTask to allow access.",
                        android.widget.Toast.LENGTH_LONG).show();
                finishStartup();
            }
        }
    }

    /** Pure, independently verifiable mapping to official Android Settings actions. */
    static Intent settingsForIssue(Context context,int issue) {
        Intent out;
        switch(issue) {
            case APP_NOTIFICATIONS:
                out=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                out.putExtra(Settings.EXTRA_APP_PACKAGE,context.getPackageName());
                return out;
            case ALARM_CHANNEL:
            case TASK_CHANNEL:
                out=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                out.putExtra(Settings.EXTRA_APP_PACKAGE,context.getPackageName());
                out.putExtra(Settings.EXTRA_CHANNEL_ID,issue==ALARM_CHANNEL?
                        TaskNotifications.activeReminderChannel(context):TaskNotifications.CHANNEL);
                return out;
            case EXACT_ALARMS:
                return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:"+context.getPackageName()));
            default:throw new IllegalArgumentException("Not a system settings issue");
        }
    }

    private void openBatterySettings() {
        // Battery allowlisting is optional; changing it is a deliberate user decision.
        launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    private void finishStartup() {
        if(completedStartup)return;
        completedStartup=true;
        if(afterStartup!=null)afterStartup.run();
    }
}
