package com.pintask.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Lightweight, dependency-free task screen. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 18, 26);
    private static final int SURFACE = Color.rgb(24, 32, 43);
    private static final int BORDER = Color.rgb(48, 59, 72);
    private static final int WHITE = Color.rgb(240, 244, 249);
    private static final int MUTED = Color.rgb(150, 163, 179);
    private static final int ACCENT = Color.rgb(175, 241, 138);
    private static final int REQUEST_NOTIFICATIONS = 7;

    private int doneLimit = 20;
    private TaskStore store;
    private EditText entry;
    private Spinner prioritySpinner;
    private Spinner repeatSpinner;
    private Spinner earlySpinner;
    private TextView dateButton;
    private long selectedDueAt;
    private LinearLayout optionalSettings;
    private LinearLayout activeContainer;
    private LinearLayout completedContainer;
    private LinearLayout warningContainer;
    private TextView countView;
    private TextView completedHeader;
    private EditText search;
    private Spinner filter;
    private UndoBar undoBar;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new TaskStore(this);
        TaskNotifications.ensureChannel(this);
        makeScreen();
        askNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        TaskNotifications.syncAll(this);
        ReminderScheduler.rescheduleAll(this);
    }

    @Override protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_NOTIFICATIONS) {
            refresh();
            TaskNotifications.syncAll(this);
            ReminderScheduler.rescheduleAll(this);
        }
    }

    private void chooseDate() {
        Calendar now = Calendar.getInstance();
        if (selectedDueAt > 0) now.setTimeInMillis(selectedDueAt);
        new DatePickerDialog(this, (datePicker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, year);
            chosen.set(Calendar.MONTH, month);
            chosen.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(this, (timePicker, hour, minute) -> {
                chosen.set(Calendar.HOUR_OF_DAY, hour);
                chosen.set(Calendar.MINUTE, minute);
                chosen.set(Calendar.SECOND, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                if (chosen.getTimeInMillis() <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Choose a future reminder time", Toast.LENGTH_LONG).show();
                    return;
                }
                selectedDueAt = chosen.getTimeInMillis();
                dateButton.setText("Reminder: " + dateLabel(selectedDueAt));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this)).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String dateLabel(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(millis));
    }

    private void addTask() {
        String title = entry.getText().toString().trim();
        if (title.isEmpty()) {
            entry.setError("Enter a task first");
            return;
        }
        int repeat = repeatSpinner.getSelectedItemPosition();
        if (repeat != TaskStore.REPEAT_NEVER && selectedDueAt <= 0) {
            Toast.makeText(this, "Choose a reminder date and time for repeating tasks", Toast.LENGTH_LONG).show();
            chooseDate();
            return;
        }
        if (selectedDueAt > 0 && selectedDueAt <= System.currentTimeMillis()) {
            Toast.makeText(this, "Reminder time must be in the future", Toast.LENGTH_LONG).show();
            return;
        }
        int beforeMinutes = selectedDueAt > 0 ? minutesForEarlySelection(earlySpinner.getSelectedItemPosition()) : -1;
        TaskStore.Task task = store.add(title, prioritySpinner.getSelectedItemPosition(),
                selectedDueAt, repeat, beforeMinutes);
        TaskNotifications.show(this, task);
        ReminderScheduler.schedule(this, task);
        entry.setText("");
        entry.clearFocus();
        optionalSettings.setVisibility(View.GONE);
        prioritySpinner.setSelection(TaskStore.PRIORITY_NORMAL);
        repeatSpinner.setSelection(TaskStore.REPEAT_NEVER);
        earlySpinner.setSelection(4); // Default: appear 15 minutes before the task.
        selectedDueAt = 0;
        dateButton.setText("+ Set reminder date & time (optional)");
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(entry.getWindowToken(), 0);
        refresh();
    }

    private void complete(TaskStore.Task task) {
        TaskStore.Completion completion = store.complete(task.id);
        if (completion != null) {
            ReminderScheduler.cancel(this, task.id);
            TaskNotifications.cancel(this, task.id);
            if (completion.nextTask != null) {
                TaskNotifications.show(this, completion.nextTask);
                ReminderScheduler.schedule(this, completion.nextTask);
            }
        }
        refresh();
        if (completion != null) undoBar.offer(task.id,this::refresh);
    }

    private void refresh() {
        if (activeContainer == null) return;
        TaskWidget.refreshAll(this);
        String query = search == null ? "" : search.getText().toString();
        int selectedFilter = filter == null ? 0 : filter.getSelectedItemPosition();
        List<TaskStore.Task> active = TaskFilters.apply(store.activePersonal(),query,selectedFilter,System.currentTimeMillis());
        List<TaskStore.Task> allDone = TaskFilters.apply(store.completedPersonal(Integer.MAX_VALUE),query,selectedFilter,System.currentTimeMillis());
        List<TaskStore.Task> recent = allDone.subList(0,Math.min(doneLimit,allDone.size()));
        countView.setText(active.size() + (active.size() == 1 ? " task" : " tasks"));
        activeContainer.removeAllViews();
        completedContainer.removeAllViews();
        warningContainer.removeAllViews();
        if (!TaskNotifications.canNotify(this)) {
            TextView warning = makeText("Notifications are off. Tap here to enable them.", 13, WHITE, false);
            warning.setPadding(dp(16), dp(13), dp(16), dp(13));
            warning.setBackground(outline(Color.rgb(70, 53, 30), 12, Color.rgb(126, 100, 52)));
            warning.setOnClickListener(v -> {
                Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(settings);
            });
            warningContainer.addView(warning, new LinearLayout.LayoutParams(-1, -2));
        }
        if (active.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(18), dp(24), dp(18), dp(24));
            empty.setBackground(outline(SURFACE, 18, BORDER));
            empty.addView(makeText("✓", 26, ACCENT, true));
            empty.addView(makeText(query.isEmpty() && selectedFilter == 0 ? "All clear." : "No matching tasks", 20, WHITE, true));
            TextView sub = makeText("Add a task above and it appears in your notifications.", 13, MUTED, false);
            sub.setPadding(0, dp(5), 0, 0);
            empty.addView(sub);
            activeContainer.addView(empty);
        } else {
            for (TaskStore.Task task : active) activeContainer.addView(taskRow(task));
        }
        int completedCount = allDone.size();
        completedHeader.setText("DONE  ·  " + completedCount);
        completedHeader.setVisibility(View.VISIBLE);
        completedContainer.setVisibility(View.VISIBLE);
        if (recent.isEmpty()) completedContainer.addView(makeText("Finished tasks will appear here.", 13, MUTED, false));
        for (TaskStore.Task task : recent) completedContainer.addView(TaskCards.done(this, task, this::refresh));
        if (completedCount > recent.size()) {
            Button more = makeButton("Show more completed tasks", SURFACE, ACCENT);
            completedContainer.addView(more);
            more.setOnClickListener(v -> { doneLimit += 20; refresh(); });
        }
    }
    private View taskRow(TaskStore.Task task) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(13), dp(10), dp(13));
        row.setBackground(outline(SURFACE, 16, BORDER));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(9);
        row.setLayoutParams(outer);
        int dotColor = task.priority == TaskStore.PRIORITY_HIGH ? Color.rgb(255, 145, 117) :
                task.priority == TaskStore.PRIORITY_LOW ? MUTED : ACCENT;
        TextView glyph = makeText("●", 16, dotColor, false);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(25), -2));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView name = makeText(task.title, 15, WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(name);
        String meta = task.priority == TaskStore.PRIORITY_HIGH ? "HIGH" :
                task.priority == TaskStore.PRIORITY_LOW ? "LOW" : "NORMAL";
        if (task.dueAt > 0) {
            meta += " · " + dateLabel(task.dueAt);
            if (task.notifyBeforeMinutes >= 0) meta += " · " +
                    (task.notifyBeforeMinutes == 0 ? "At task time" :
                            task.notifyBeforeMinutes + " min before");
        }
        if (task.recurrence != TaskStore.REPEAT_NEVER) {
            meta += task.recurrence == TaskStore.REPEAT_DAILY ? " · Daily" :
                    task.recurrence == TaskStore.REPEAT_WEEKLY ? " · Weekly" : " · Monthly";
        }
        if (task.snoozedUntil > System.currentTimeMillis()) meta += " · Snoozed until " + dateLabel(task.snoozedUntil);
        TextView info = makeText(meta, 11, MUTED, false);
        info.setPadding(0, dp(5), 0, 0);
        details.addView(info);
        TextView timing = makeText(task.dueAt == 0 ? "No time · Add time" : "Change / remove time", 12, ACCENT, true);
        timing.setPadding(0, dp(10), 0, dp(10));
        timing.setOnClickListener(v -> TaskTimingEditor.open(this, task, this::refresh));
        details.addView(timing);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        Button done = makeButton("Done", ACCENT, BG);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(74), dp(43));
        doneParams.leftMargin = dp(9);
        row.addView(done, doneParams);
        done.setOnClickListener(v -> complete(task));
        row.addView(TaskCards.overflow(this, task, this::refresh));
        return row;
    }

    private Spinner makeSpinner(String[] options, int initial) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(WHITE);
                view.setTextSize(14);
                view.setPadding(dp(9), dp(9), dp(9), dp(9));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(initial);
        spinner.setBackground(outline(BG, 10, BORDER));
        return spinner;
    }

    private void makeScreen() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(19), dp(22), dp(19), dp(22));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(dp(19) + bars.left, dp(22) + bars.top,
                        dp(19) + bars.right, dp(22) + bars.bottom);
                return insets;
            });
        } else {
            root.setFitsSystemWindows(true);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        body.addView(makeText("P I N T A S K     /     STAY ON TRACK", 11, ACCENT, true));
        TextView title = makeText("Your tasks", 26, WHITE, true);
        LinearLayout.LayoutParams heading = new LinearLayout.LayoutParams(-1, -2);
        heading.topMargin = dp(14);
        body.addView(title, heading);
        TextView blurb = makeText("Add a task now. Set a time whenever you want.", 14, MUTED, false);
        blurb.setPadding(0, dp(10), 0, dp(23));
        body.addView(blurb);
        LinearLayout navigation = new LinearLayout(this);
        body.addView(navigation,new LinearLayout.LayoutParams(-1,dp(48)));
        Button shiftMode = makeButton("Shifts", SURFACE, ACCENT);
        LinearLayout.LayoutParams shiftNav = new LinearLayout.LayoutParams(-1, dp(48));
        shiftNav.bottomMargin = dp(16);
        navigation.addView(shiftMode,new LinearLayout.LayoutParams(0,dp(48),1));
        shiftMode.setOnClickListener(v -> startActivity(new Intent(this, ShiftActivity.class)));
        Button favorites = makeButton("★ Favorites", SURFACE, ACCENT);
        navigation.addView(favorites,new LinearLayout.LayoutParams(0,dp(48),1));
        favorites.setOnClickListener(v -> showFavorites(false));
        Button backup = makeButton("Backup", SURFACE, WHITE);
        navigation.addView(backup,new LinearLayout.LayoutParams(0,dp(48),1));
        backup.setOnClickListener(v -> startActivity(new Intent(this,BackupActivity.class)));

        LinearLayout compose = new LinearLayout(this);
        compose.setOrientation(LinearLayout.VERTICAL);
        compose.setPadding(dp(15), dp(14), dp(15), dp(15));
        compose.setBackground(outline(SURFACE, 18, BORDER));
        body.addView(compose);
        compose.addView(makeText("NEW TASK", 11, MUTED, true));
        entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setTextSize(17);
        entry.setTextColor(WHITE);
        entry.setHintTextColor(MUTED);
        entry.setHint("What do you need to do?");
        entry.setPadding(0, dp(10), 0, dp(12));
        entry.setBackgroundColor(Color.TRANSPARENT);
        entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        entry.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                addTask();
                return true;
            }
            return false;
        });
        compose.addView(entry, new LinearLayout.LayoutParams(-1, -2));

        Button options = makeButton("Priority, time & repeat (optional)", SURFACE, ACCENT);
        compose.addView(options, new LinearLayout.LayoutParams(-1, dp(46)));
        optionalSettings = new LinearLayout(this);
        optionalSettings.setOrientation(LinearLayout.VERTICAL);
        optionalSettings.setVisibility(View.GONE);
        compose.addView(optionalSettings, new LinearLayout.LayoutParams(-1, -2));
        options.setOnClickListener(v -> optionalSettings.setVisibility(
                optionalSettings.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        LinearLayout selectors = new LinearLayout(this);
        selectors.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams selectsParams = new LinearLayout.LayoutParams(-1, -2);
        selectsParams.bottomMargin = dp(12);
        optionalSettings.addView(selectors, selectsParams);
        LinearLayout priorityBox = new LinearLayout(this);
        priorityBox.setOrientation(LinearLayout.VERTICAL);
        priorityBox.addView(makeText("PRIORITY", 10, MUTED, true));
        prioritySpinner = makeSpinner(new String[]{"Low", "Normal", "High"}, 1);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, dp(43));
        controlParams.topMargin = dp(5);
        priorityBox.addView(prioritySpinner, controlParams);
        LinearLayout repeatBox = new LinearLayout(this);
        repeatBox.setOrientation(LinearLayout.VERTICAL);
        repeatBox.addView(makeText("REPEAT", 10, MUTED, true));
        repeatSpinner = makeSpinner(new String[]{"Never", "Daily", "Weekly", "Monthly"}, 0);
        LinearLayout.LayoutParams repeatControl = new LinearLayout.LayoutParams(-1, dp(43));
        repeatControl.topMargin = dp(5);
        repeatBox.addView(repeatSpinner, repeatControl);
        LinearLayout.LayoutParams halfLeft = new LinearLayout.LayoutParams(0, -2, 1);
        halfLeft.rightMargin = dp(8);
        selectors.addView(priorityBox, halfLeft);
        selectors.addView(repeatBox, new LinearLayout.LayoutParams(0, -2, 1));

        dateButton = makeText("+ Set reminder date & time (optional)", 13, ACCENT, true);
        dateButton.setGravity(Gravity.CENTER_VERTICAL);
        dateButton.setPadding(dp(12), dp(11), dp(12), dp(11));
        dateButton.setBackground(outline(BG, 12, BORDER));
        dateButton.setOnClickListener(v -> chooseDate());
        optionalSettings.addView(dateButton, new LinearLayout.LayoutParams(-1, -2));

        TextView earlyLabel = makeText("SHOW NOTIFICATION", 10, MUTED, true);
        earlyLabel.setPadding(0, dp(13), 0, dp(4));
        optionalSettings.addView(earlyLabel);
        earlySpinner = makeSpinner(new String[]{"Immediately", "At task time", "5 min before",
                "10 min before", "15 min before", "30 min before", "1 hour before"}, 4);
        LinearLayout.LayoutParams earlyParams = new LinearLayout.LayoutParams(-1, dp(44));
        earlyParams.bottomMargin = dp(5);
        optionalSettings.addView(earlySpinner, earlyParams);

        TextView clearDate = makeText("Clear reminder time", 12, MUTED, false);
        clearDate.setGravity(Gravity.END);
        clearDate.setPadding(0, dp(10), 0, dp(10));
        clearDate.setOnClickListener(v -> {
            selectedDueAt = 0;
            dateButton.setText("+ Set reminder date & time (optional)");
        });
        optionalSettings.addView(clearDate);
        Button add = makeButton("+  Add task", ACCENT, BG);
        compose.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
        add.setOnClickListener(v -> addTask());

        search = new EditText(this);
        search.setSingleLine(true); search.setHint("Search tasks and Done history");
        search.setTextColor(WHITE); search.setHintTextColor(MUTED);
        body.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        filter = makeSpinner(new String[]{"All tasks", "Overdue", "Today", "No time", "High priority", "Normal priority", "Low priority"},0);
        body.addView(filter,new LinearLayout.LayoutParams(-1,dp(48)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { doneLimit=20; refresh(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id) { doneLimit=20; refresh(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        undoBar = new UndoBar(this);
        body.addView(undoBar,new LinearLayout.LayoutParams(-1,-2));

        warningContainer = new LinearLayout(this);
        warningContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams warn = new LinearLayout.LayoutParams(-1, -2);
        warn.topMargin = dp(12);
        body.addView(warningContainer, warn);
        LinearLayout activeHead = new LinearLayout(this);
        activeHead.setOrientation(LinearLayout.HORIZONTAL);
        activeHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams activeHeadParams = new LinearLayout.LayoutParams(-1, -2);
        activeHeadParams.topMargin = dp(29);
        activeHeadParams.bottomMargin = dp(12);
        body.addView(activeHead, activeHeadParams);
        activeHead.addView(makeText("TO DO", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        countView = makeText("0 tasks", 13, ACCENT, true);
        activeHead.addView(countView);
        activeContainer = new LinearLayout(this);
        activeContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(activeContainer);
        completedHeader = makeText("COMPLETED", 12, MUTED, true);
        LinearLayout.LayoutParams comp = new LinearLayout.LayoutParams(-1, -2);
        comp.topMargin = dp(27);
        comp.bottomMargin = dp(11);
        body.addView(completedHeader, comp);
        completedContainer = new LinearLayout(this);
        completedContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(completedContainer);
        TextView foot = makeText("Undated tasks appear immediately. Dated tasks can appear at a chosen time before they're due and stay until Done. Android battery settings may delay alerts; Android 14+ may allow swiping away an ongoing notification.", 12, MUTED, false);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(-1, -2);
        footParams.topMargin = dp(28);
        body.addView(foot, footParams);
    }

    private static int minutesForEarlySelection(int position) {
        switch (position) {
            case 1: return 0;
            case 2: return 5;
            case 3: return 10;
            case 4: return 15;
            case 5: return 30;
            case 6: return 60;
            default: return -1;
        }
    }

    private void showFavorites(boolean remove) {
        List<TaskStore.Task> saved = store.favorites();
        if (saved.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Favorites").setMessage("Use a task's ⋮ menu → Save as favorite. Add it again here with one tap, without a time.")
                    .setPositiveButton("OK",null).show(); return;
        }
        String[] names = new String[saved.size()];
        for (int i=0;i<saved.size();i++) names[i]=saved.get(i).title;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(remove ? "Remove a favorite" : "Tap a favorite to add it")
                .setItems(names,(d,index)->{
                    if (remove) {
                        new AlertDialog.Builder(this).setTitle("Remove favorite?")
                                .setMessage("Existing tasks stay unchanged.").setNegativeButton("Cancel",null)
                                .setPositiveButton("Remove",(confirm,w)->store.removeFavorite(saved.get(index).id)).show();
                    } else {
                        TaskNotifications.show(this,store.addFavorite(saved.get(index).id)); refresh();
                        Toast.makeText(this,"Added without a time",Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Close",null);
        if (!remove) dialog.setNeutralButton("Manage favorites",(d,w)->showFavorites(true));
        dialog.show();
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button makeButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(foreground);
        button.setBackground(outline(background, 12, background));
        return button;
    }

    private GradientDrawable outline(int fill, int corners, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(corners));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
