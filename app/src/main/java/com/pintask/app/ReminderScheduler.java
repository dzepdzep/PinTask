package com.pintask.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Exact reminders when permitted, with an inexact fallback. */
public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static boolean canScheduleExact(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return manager != null && (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms());
    }

    private static PendingIntent alarm(Context context, long id, int flags) {
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(TaskNotifications.ACTION_REMINDER);
        intent.putExtra(TaskNotifications.EXTRA_ID, id);
        return PendingIntent.getBroadcast(context, (int) id, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context context, TaskStore.Task task) {
        if (task == null || task.done || !task.reminderEnabled() || (task.dueAt <= 0 && task.shiftRunId == 0 && task.snoozedUntil == 0) || task.remindedAt > 0) return;
        ReminderWatchdog.ensureScheduled(context);
        long notifyAt = task.notificationAt();
        if (notifyAt <= System.currentTimeMillis()) {
            // A task might have become overdue while Android held the process.
            deliver(context, task.id, "reschedule_overdue");
            return;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) {
            ReminderAudit.note(context, "schedule_no_alarm_manager", task.id, notifyAt);
            return;
        }
        PendingIntent pending = alarm(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exactPermitted = canScheduleExact(context);
        if (exactPermitted) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pending);
                ReminderAudit.note(context, "scheduled_exact", task.id, notifyAt);
                return;
            } catch (SecurityException denied) {
                // A permission may have been revoked between checking and scheduling.
                ReminderAudit.note(context, "exact_permission_revoked", task.id, notifyAt);
            }
        }
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pending);
            ReminderAudit.note(context, "scheduled_inexact", task.id, notifyAt);
        } catch (RuntimeException failure) {
            ReminderAudit.note(context, "schedule_failed", task.id, notifyAt);
            android.util.Log.e("PinTask", "Could not schedule task alarm " + task.id, failure);
        }
    }

    public static void cancel(Context context, long id) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        PendingIntent pending = alarm(context, id, PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
            ReminderAudit.note(context, "cancelled", id, 0);
        }
    }

    public static void rescheduleAll(Context context) {
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.activeAll()) schedule(context, task);
        }
    }

    /** One wake-up drains all due occurrences; simultaneous alarms remain idempotent. */
    public static void deliverDue(Context context) {
        deliverDue(context, "recovery_check");
    }

    static void deliverDue(Context context, String origin) {
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.activeAll()) {
                if (task.remindedAt == 0 && task.notificationAt() <= System.currentTimeMillis())
                    deliver(context, task.id, origin);
            }
        }
    }

    public static void deliver(Context context, long id) {
        deliver(context, id, "direct");
    }

    static void deliver(Context context, long id, String origin) {
        try (TaskStore store = new TaskStore(context)) {
            // Don't consume a reminder if notifications are currently disabled.
            if (!TaskNotifications.canNotify(context)) {
                ReminderAudit.note(context, "delivery_blocked_app_notifications", id, 0);
                return;
            }
            TaskStore.Task candidate = store.find(id);
            if (candidate == null) return;
            String channel = candidate.dueAt == 0 && candidate.snoozedUntil == 0
                    ? TaskNotifications.CHANNEL : TaskNotifications.activeReminderChannel(context);
            if (!TaskNotifications.canNotifyChannel(context, channel)) {
                ReminderAudit.note(context, "delivery_blocked_channel", id, candidate.notificationAt());
                return;
            }
            TaskStore.Task task = store.claimDueReminder(id);
            if (task != null) {
                try {
                    if (task.notifyBeforeMinutes >= 0 || task.dueAt == 0 || task.snoozedUntil > 0) {
                        // The scheduled task now becomes a pinned notification; no duplicate alert.
                        TaskNotifications.show(context, task);
                    } else {
                        // Old behavior for tasks configured to appear immediately.
                        TaskNotifications.showDue(context, task);
                    }
                    ReminderAudit.note(context, "delivered_" + origin, id, task.notificationAt());
                    if (System.currentTimeMillis() - task.notificationAt() > 120_000L) {
                        ReminderAudit.note(context, "late_delivery_" + origin, id, task.notificationAt());
                        if (!"alarm".equals(origin))
                            ReminderAudit.note(context, "recovered_by_" + origin, id, task.notificationAt());
                    }
                    // Only new opt-in routines auto-create their next independent occurrence.
                    // This must happen after successful notification posting.
                    TaskStore.Task following=store.createNextRoutineOccurrence(id);
                    if(following!=null) schedule(context,following);
                } catch (RuntimeException failure) {
                    store.getWritableDatabase().execSQL("UPDATE tasks SET reminded_at=0 WHERE _id=? AND done=0", new Object[]{id});
                    ReminderAudit.note(context,"delivery_error",id,candidate.notificationAt());
                    android.util.Log.w("PinTask", "Reminder posting failed for task " + id, failure);
                }
            }
        }
    }
}
