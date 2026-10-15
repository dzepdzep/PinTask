package com.pintask.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.text.DateFormat;
import java.util.Date;

/** Pinned task notifications, separate alert reminders, and inline Quick Add. */
public final class TaskNotifications {
    public static final String CHANNEL = "unfinished_tasks";
    public static final String REMINDER_CHANNEL = "due_reminders_v2";
    public static final String ACTION_DONE = "com.pintask.app.ACTION_DONE";
    public static final String ACTION_UNDO = "com.pintask.app.ACTION_UNDO";
    public static final String ACTION_DISMISSED = "com.pintask.app.ACTION_DISMISSED";
    public static final String ACTION_REMINDER = "com.pintask.app.ACTION_REMINDER";
    public static final String ACTION_QUICK_ADD = "com.pintask.app.ACTION_QUICK_ADD";
    public static final String EXTRA_ID = "task_id";
    public static final String REMOTE_TEXT = "quick_task_text";
    private static final int QUICK_ID = 1999999999;
    private static final int REMINDER_OFFSET = 1000000000;

    private TaskNotifications() {}

    public static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel persistent = new NotificationChannel(
                CHANNEL, "Unfinished tasks", NotificationManager.IMPORTANCE_LOW);
        persistent.setDescription("Unfinished tasks and Quick Add shortcut");
        persistent.enableVibration(false);
        persistent.setSound(null, null);
        manager.createNotificationChannel(persistent);
        NotificationChannel due = new NotificationChannel(
                REMINDER_CHANNEL, "Due reminders", NotificationManager.IMPORTANCE_HIGH);
        due.setDescription("Alerts when a scheduled task is due");
        manager.createNotificationChannel(due);
    }

    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager != null && manager.areNotificationsEnabled();
    }

    private static PendingIntent open(Context context) {
        Intent intent = new Intent(context, ShiftActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.Action doneAction(Context context, long id) {
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(ACTION_DONE);
        intent.putExtra(EXTRA_ID, id);
        PendingIntent callback = PendingIntent.getBroadcast(context, (int) id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_check), "Done", callback).build();
    }

    private static String priorityText(int priority) {
        return priority == TaskStore.PRIORITY_HIGH ? "High priority" :
                priority == TaskStore.PRIORITY_LOW ? "Low priority" : "Normal priority";
    }

    private static Notification.Action snoozeAction(Context context, long id) {
        Intent intent = new Intent(context, SnoozeActivity.class).putExtra(EXTRA_ID,id);
        PendingIntent pending = PendingIntent.getActivity(context,(int)id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(context,R.drawable.ic_pin),"Snooze",pending).build();
    }

    public static void show(Context context, TaskStore.Task task) {
        TaskWidget.refreshAll(context);
        if (task == null || task.done || !task.reminderEnabled() || !canNotify(context)) return;
        if (task.snoozedUntil > System.currentTimeMillis()) return;
        if (task.dueAt == 0 && task.shiftRunId > 0 && System.currentTimeMillis() < task.notificationAt()) return;
        // Scheduled tasks appear only when their selected advance interval begins.
        if (task.dueAt > 0 && task.notifyBeforeMinutes >= 0 &&
                System.currentTimeMillis() < task.notificationAt()) return;
        ensureChannel(context);
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(ACTION_DISMISSED);
        intent.putExtra(EXTRA_ID, task.id);
        PendingIntent dismissed = PendingIntent.getBroadcast(context, (int) task.id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String detail = (task.shiftRunId > 0 ? "Shift · " + task.shiftLabel + "  ·  " : "") + priorityText(task.priority);
        if (task.dueAt > 0) detail += "  ·  Due " + DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date(task.dueAt));
        // Use the reminder channel for scheduled arrivals, so the first notification alerts.
        String channel = task.snoozedUntil > 0 || (task.notifyBeforeMinutes >= 0 && task.dueAt > 0)
                ? REMINDER_CHANNEL : CHANNEL;
        Notification notification = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle(task.title)
                .setContentText(detail)
                .setSubText("PinTask · Tap Done to clear")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open(context))
                .setDeleteIntent(dismissed)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(task.createdAt)
                .addAction(doneAction(context, task.id)).addAction(snoozeAction(context, task.id)).build();
        // Android 14+ permits individual dismissal despite these flags.
        notification.flags |= Notification.FLAG_NO_CLEAR;
        context.getSystemService(NotificationManager.class).notify((int) task.id, notification);
    }

    public static void showDue(Context context, TaskStore.Task task) {
        if (!canNotify(context)) return;
        ensureChannel(context);
        Notification notification = new Notification.Builder(context, REMINDER_CHANNEL)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("Due now: " + task.title)
                .setContentText("Your scheduled PinTask reminder")
                .setContentIntent(open(context))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(doneAction(context, task.id)).addAction(snoozeAction(context, task.id)).build();
        context.getSystemService(NotificationManager.class)
                .notify(REMINDER_OFFSET + (int) task.id, notification);
    }

    public static void showUndo(Context context,long id) {
        if(!canNotify(context)) return;
        ensureChannel(context);
        Intent intent=new Intent(context,TaskActionReceiver.class).setAction(ACTION_UNDO).putExtra(EXTRA_ID,id);
        PendingIntent undo=PendingIntent.getBroadcast(context,(int)id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_check)
                .setContentTitle("Task completed").setContentText("Undo is available for 10 seconds")
                .setTimeoutAfter(10_000).setContentIntent(open(context))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(context,R.drawable.ic_pin),"Undo",undo).build()).build();
        context.getSystemService(NotificationManager.class).notify("undo",(int)id,notification);
    }

    /** v2 deliberately requires fresh opt-in after older always-on builds. */
    public static boolean quickAddEnabled(Context context) {
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean("quick_add_opt_in_v2",false);
    }
    public static void setQuickAddEnabled(Context context,boolean enabled) {
        clearQuickAdd(context);
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().remove("quick_add").putBoolean("quick_add_opt_in_v2",enabled).apply();
        showQuickAdd(context);
    }
    private static void clearQuickAdd(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);if(manager==null)return;
        manager.cancel(QUICK_ID);
        // Older builds may have used a tag or a different ID. Only remove the shortcut.
        for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications()) {
            boolean shortcut=active.getId()==QUICK_ID;
            Notification notification=active.getNotification();
            if(notification.actions!=null)for(Notification.Action action:notification.actions) {
                RemoteInput[] inputs=action.getRemoteInputs();if(inputs!=null)for(RemoteInput input:inputs)if(REMOTE_TEXT.equals(input.getResultKey()))shortcut=true;
            }
            if(shortcut)manager.cancel(active.getTag(),active.getId());
        }
        Intent intent=new Intent(context,TaskActionReceiver.class).setAction(ACTION_QUICK_ADD);
        PendingIntent old=PendingIntent.getBroadcast(context,QUICK_ID,intent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_MUTABLE);
        if(old!=null)old.cancel();
    }

    public static void showQuickAdd(Context context) {
        if(!quickAddEnabled(context)) { clearQuickAdd(context);return; }
        if (!canNotify(context)) return;
        ensureChannel(context);
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(ACTION_QUICK_ADD);
        // RemoteInput requires a mutable PendingIntent on Android 12+.
        PendingIntent submit = PendingIntent.getBroadcast(context, QUICK_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput text = new RemoteInput.Builder(REMOTE_TEXT)
                .setLabel("New task name")
                .build();
        Notification.Action add = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_check), "Add task", submit)
                .addRemoteInput(text)
                .setAllowGeneratedReplies(true)
                .build();
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("PinTask · Quick Add")
                .setContentText("Add tasks without opening the app")
                .setContentIntent(open(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(add).build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        context.getSystemService(NotificationManager.class).notify(QUICK_ID, notification);
    }

    public static void syncAll(Context context) {
        showQuickAdd(context);
        if (!canNotify(context)) return;
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.active()) show(context, task);
        }
    }

    public static void cancel(Context context, long id) {
        TaskWidget.refreshAll(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.cancel((int) id);
        manager.cancel("undo",(int)id);
        manager.cancel(REMINDER_OFFSET + (int) id);
    }
}
