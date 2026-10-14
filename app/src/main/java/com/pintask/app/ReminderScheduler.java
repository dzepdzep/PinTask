package com.pintask.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Inexact, battery-friendly alarms: no special exact-alarm permission needed. */
public final class ReminderScheduler {
    private ReminderScheduler() {}

    private static PendingIntent alarm(Context context, long id, int flags) {
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(TaskNotifications.ACTION_REMINDER);
        intent.putExtra(TaskNotifications.EXTRA_ID, id);
        return PendingIntent.getBroadcast(context, (int) id, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context context, TaskStore.Task task) {
        if (task == null || task.done || task.dueAt <= 0 || task.remindedAt > 0) return;
        long notifyAt = task.notificationAt();
        if (notifyAt <= System.currentTimeMillis()) {
            deliver(context, task.id);
            return;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt,
                alarm(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void cancel(Context context, long id) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        PendingIntent pending = alarm(context, id, PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    public static void rescheduleAll(Context context) {
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.active()) schedule(context, task);
        }
    }

    public static void deliver(Context context, long id) {
        try (TaskStore store = new TaskStore(context)) {
            // Don't consume the reminder if notifications are currently disabled.
            if (!TaskNotifications.canNotify(context)) return;
            TaskStore.Task task = store.claimDueReminder(id);
            if (task != null) {
                if (task.notifyBeforeMinutes >= 0) {
                    // The scheduled task now becomes a pinned notification; no duplicate alert.
                    TaskNotifications.show(context, task);
                } else {
                    // Old behavior for tasks configured to appear immediately.
                    TaskNotifications.showDue(context, task);
                }
            }
        }
    }
}
