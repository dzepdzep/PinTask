package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;

public final class QuickAddActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        EditText entry = new EditText(this);
        entry.setHint("What do you need to do?"); entry.setSingleLine(true);
        entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add task · No time needed").setView(entry)
                .setPositiveButton("Add",null).setNegativeButton("Cancel",(d,w)->finish())
                .setOnCancelListener(d->finish()).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if (entry.getText().toString().trim().isEmpty()) { entry.setError("Enter a task"); return; }
            try (TaskStore store = new TaskStore(this)) { TaskNotifications.show(this,store.add(entry.getText().toString())); }
            dialog.dismiss(); finish();
        }));
        dialog.show();
    }
}
