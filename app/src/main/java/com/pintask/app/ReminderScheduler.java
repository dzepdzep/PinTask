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
            deliver(context, task.id);
            return;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        PendingIntent pending = alarm(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT);
        if (canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pending);
                if(task.autoRepeat)ReminderAudit.note(context,"scheduled_exact",task.id,notifyAt);
                return;
            } catch (SecurityException denied) {
                // Keep a reminder scheduled if exact access is unavailable.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pending);
        if(task.autoRepeat)ReminderAudit.note(context,"scheduled_inexact",task.id,notifyAt);
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
            for (TaskStore.Task task : store.activeAll()) schedule(context, task);
        }
    }

    /** One wake-up drains all due occurrences; simultaneous alarms remain idempotent. */
    public static void deliverDue(Context context) {
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.activeAll()) {
                if (task.remindedAt == 0 && task.notificationAt() <= System.currentTimeMillis())
                    deliver(context, task.id);
            }
        }
    }

    public static void deliver(Context context, long id) {
        try (TaskStore store = new TaskStore(context)) {
            // Don't consume the reminder if notifications are currently disabled.
            if (!TaskNotifications.canNotify(context)) return;
            TaskStore.Task candidate = store.find(id);
            if (candidate == null) return;
            String channel = candidate.dueAt == 0 && candidate.snoozedUntil == 0
                    ? TaskNotifications.CHANNEL : TaskNotifications.activeReminderChannel(context);
            if (!TaskNotifications.canNotifyChannel(context, channel)) return;
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
                    ReminderAudit.note(context,"delivered",id,task.notificationAt());
                    if(System.currentTimeMillis()-task.notificationAt()>120_000L)
                        ReminderAudit.note(context,"overdue_recovered",id,task.notificationAt());
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
