package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Toast;
import java.util.Calendar;

/** Direct notification destination, avoiding background activity launches from receivers. */
public final class SnoozeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        long id = getIntent().getLongExtra(TaskNotifications.EXTRA_ID, -1);
        // The user already tapped Snooze: silence the active sound while they
        // choose a new time, rather than ringing throughout the picker.
        AlarmSoundService.stopForTask(this,id);
        new AlertDialog.Builder(this).setTitle("Snooze reminder")
                .setItems(new String[]{"5 minutes", "15 minutes", "30 minutes", "Tomorrow at this time", "Choose date and time…"}, (d, which) -> {
                    Calendar c = Calendar.getInstance();
                    if (which < 3) { c.add(Calendar.MINUTE, new int[]{5,15,30}[which]); save(id, c.getTimeInMillis()); }
                    else if (which == 3) { c.add(Calendar.DAY_OF_MONTH, 1); save(id, c.getTimeInMillis()); }
                    else { DatePickerDialog datePicker = new DatePickerDialog(this, (date, y, m, day) -> {
                        c.set(y, m, day);
                        TimePickerDialog picker = new TimePickerDialog(this, (time,h,min) -> {
                            c.set(Calendar.HOUR_OF_DAY,h); c.set(Calendar.MINUTE,min);
                            c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
                            save(id,c.getTimeInMillis());
                        }, c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),android.text.format.DateFormat.is24HourFormat(this));
                        picker.setOnCancelListener(dialog -> finish()); picker.show();
                    }, c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
                        datePicker.setOnCancelListener(dialog -> finish()); datePicker.show(); }
                }).setNegativeButton("Cancel", (d,w) -> finish()).setOnCancelListener(d -> finish()).show();
    }

    private void save(long id,long time) {
        try (TaskStore store = new TaskStore(this)) {
            TaskStore.Task task = store.snooze(id,time);
            ReminderScheduler.cancel(this,id); TaskNotifications.cancel(this,id);
            ReminderScheduler.schedule(this,task);
            Toast.makeText(this,"Reminder snoozed",Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        finish();
    }
}
