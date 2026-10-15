package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.widget.Toast;
import java.util.Calendar;

/** Add, change or remove timing without replacing the original task. */
final class TaskTimingEditor {
    static void open(Activity activity, TaskStore.Task task, Runnable refresh) {
        new AlertDialog.Builder(activity).setTitle(task.title)
                .setItems(new String[]{"Set / change date and time", "No time — do whenever"}, (d, which) -> {
                    if (which == 1) { save(activity, task, 0, task.notifyBeforeMinutes == TaskStore.REMINDER_OFF ? TaskStore.REMINDER_OFF : -1, refresh); return; }
                    Calendar selected = Calendar.getInstance();
                    if (task.dueAt > System.currentTimeMillis()) selected.setTimeInMillis(task.dueAt);
                    new DatePickerDialog(activity, (date, y, m, day) -> {
                        selected.set(y, m, day);
                        new TimePickerDialog(activity, (time, h, min) -> {
                            selected.set(Calendar.HOUR_OF_DAY, h);
                            selected.set(Calendar.MINUTE, min);
                            selected.set(Calendar.SECOND, 0);
                            selected.set(Calendar.MILLISECOND, 0);
                            int[] early = {-2, 0, 5, 10, 15, 30, 60};
                            new AlertDialog.Builder(activity).setTitle("Show notification")
                                    .setItems(new String[]{"No reminder", "At task time", "5 minutes before", "10 minutes before",
                                            "15 minutes before", "30 minutes before", "1 hour before"},
                                            (dialog, index) -> save(activity, task, selected.getTimeInMillis(), early[index], refresh))
                                    .setNegativeButton("Cancel", null).show();
                        }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE),
                                android.text.format.DateFormat.is24HourFormat(activity)).show();
                    }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show();
                }).setNegativeButton("Cancel", null).show();
    }

    private static void save(Activity activity, TaskStore.Task task, long due, int early, Runnable refresh) {
        try (TaskStore store = new TaskStore(activity)) {
            TaskStore.Task updated = store.updateTiming(task.id, due, task.recurrence, early);
            ReminderScheduler.cancel(activity, task.id);
            TaskNotifications.cancel(activity, task.id);
            TaskNotifications.show(activity, updated);
            ReminderScheduler.schedule(activity, updated);
            refresh.run();
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
