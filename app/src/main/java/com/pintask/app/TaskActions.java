package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;
import java.util.Calendar;

/** Shared overflow actions for To do and Done cards. */
final class TaskActions {
    private interface Change { TaskStore.Task apply(TaskStore store); }

    static void show(Activity activity, View anchor, TaskStore.Task task, Runnable refresh) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        item(menu, "Save as favorite", () -> {
            try (TaskStore store = new TaskStore(activity)) { store.saveFavorite(task.id); }
            Toast.makeText(activity,"Saved as a reusable untimed task",Toast.LENGTH_SHORT).show();
        });
        if (task.done) {
            item(menu, task.shiftRunId > 0 ? "Repeat as a personal task" : "Repeat now", () -> change(activity, refresh,
                    store -> store.repeat(task.id, 0, TaskStore.REPEAT_NEVER, -1)));
            item(menu, "Repeat at a chosen time…", () -> schedule(activity, task, refresh, true, 0));
            item(menu, "Make a recurring personal task…", () -> recurrence(activity, task, refresh, true));
            if (task.shiftRunId == 0 && task.recurrence == 0)
                item(menu, "Undo completion (no time)", () -> change(activity, refresh, store -> store.reopen(task.id)));
        } else {
            item(menu, "Change / remove time…", () -> TaskTimingEditor.open(activity, task, refresh));
            if (task.shiftRunId == 0) {
                item(menu, "Repeat daily / weekly / monthly…", () -> recurrence(activity, task, refresh, false));
                if (task.recurrence != 0) item(menu, "Stop repeating", () -> change(activity, refresh, store -> {
                    store.stopRepeating(task.id); return store.find(task.id);
                }));
            }
            item(menu, "Snooze…", () -> activity.startActivity(new android.content.Intent(activity,SnoozeActivity.class)
                    .putExtra(TaskNotifications.EXTRA_ID,task.id)));
            item(menu, "Change priority…", () -> new AlertDialog.Builder(activity).setTitle("Priority")
                    .setItems(new String[]{"Low", "Normal", "High"}, (d, which) ->
                            change(activity, refresh, store -> store.changePriority(task.id, which))).show());
        }
        item(menu, "Rename…", () -> {
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
            input.setText(task.title);
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Rename task").setView(input)
                    .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (input.getText().toString().trim().isEmpty()) { input.setError("Enter a task name"); return; }
                change(activity, refresh, store -> store.rename(task.id, input.getText().toString()));
                dialog.dismiss();
            }));
            dialog.show();
        });
        item(menu, "Delete…", () -> new AlertDialog.Builder(activity).setTitle("Delete this task?")
                .setMessage("This removes only this item. Other occurrences and shift templates stay unchanged.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> {
                    try (TaskStore store = new TaskStore(activity)) { store.delete(task.id); }
                    ReminderScheduler.cancel(activity, task.id);
                    TaskNotifications.cancel(activity, task.id);
                    refresh.run();
                }).show());
        menu.show();
    }

    private static void item(PopupMenu menu, String label, Runnable action) {
        menu.getMenu().add(label).setOnMenuItemClickListener(item -> { action.run(); return true; });
    }

    private static void recurrence(Activity activity, TaskStore.Task task, Runnable refresh, boolean copy) {
        new AlertDialog.Builder(activity).setTitle(copy ? "Start a new repeating task" : "Repeat this task")
                .setMessage(copy ? "Keeps this Done record. Any already scheduled repeats stay unchanged." :
                        "The next occurrence is created when you mark this task Done.")
                .setPositiveButton("Choose repeat", (d, w) -> new AlertDialog.Builder(activity)
                        .setTitle("How often?").setItems(new String[]{"Daily", "Weekly", "Monthly"},
                                (dialog, index) -> schedule(activity, task, refresh, copy, index + 1))
                        .setNegativeButton("Cancel", null).show())
                .setNegativeButton("Cancel", null).show();
    }

    private static void schedule(Activity activity, TaskStore.Task task, Runnable refresh, boolean copy, int recurrence) {
        Calendar selected = Calendar.getInstance();
        selected.add(Calendar.HOUR_OF_DAY, 1);
        if (!copy && task.dueAt > System.currentTimeMillis()) selected.setTimeInMillis(task.dueAt);
        DatePickerDialog date = new DatePickerDialog(activity, (picker, y, m, day) -> {
            selected.set(y, m, day);
            new TimePickerDialog(activity, (time, h, min) -> {
                selected.set(Calendar.HOUR_OF_DAY, h);
                selected.set(Calendar.MINUTE, min);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                change(activity, refresh, store -> copy ? store.repeat(task.id, selected.getTimeInMillis(), recurrence, 0) :
                        store.updateTiming(task.id, selected.getTimeInMillis(), recurrence, 0));
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(activity)).show();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        date.setTitle("First task date");
        date.show();
    }

    private static void change(Activity activity, Runnable refresh, Change change) {
        try (TaskStore store = new TaskStore(activity)) {
            TaskStore.Task task = change.apply(store);
            if (task != null) {
                ReminderScheduler.cancel(activity, task.id);
                TaskNotifications.cancel(activity, task.id);
                TaskNotifications.show(activity, task);
                ReminderScheduler.schedule(activity, task);
            }
            refresh.run();
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
