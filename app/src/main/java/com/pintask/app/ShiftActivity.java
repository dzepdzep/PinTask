package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Optional shift scheduler: reusable templates, fixed and interval rules, overnight support. */
public final class ShiftActivity extends Activity {
    private static final int BG = Color.rgb(13, 18, 26);
    private static final int SURFACE = Color.rgb(24, 32, 43);
    private static final int BORDER = Color.rgb(48, 59, 72);
    private static final int WHITE = Color.rgb(240, 244, 249);
    private static final int MUTED = Color.rgb(150, 163, 179);
    private static final int ACCENT = Color.rgb(175, 241, 138);
    private static final int[] INTERVALS = {0, 30, 60, 120, 180, 240};
    private static final int[] EARLY = {0, 5, 10, 15, 30, 60};

    private ShiftRepository repository;
    private List<ShiftRepository.Template> templates = new ArrayList<>();
    private Spinner templateSpinner, intervalSpinner, earlySpinner, prioritySpinner;
    private EditText templateName, stepName;
    private TextView newStartView, newEndView, stepTimeView, selectedTemplateView, dateView;
    private LinearLayout stepsArea, currentArea;
    private int newStart = 8 * 60, newEnd = 20 * 60, stepClock = 8 * 60;
    private final Calendar selectedDate = Calendar.getInstance();
    private long selectedTemplateId = -1;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new ShiftRepository(this);
        buildScreen();
        refreshTemplates(-1);
        refreshCurrentRun();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repository != null && currentArea != null) refreshCurrentRun();
    }

    @Override protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }

    private void refreshTemplates(long chooseId) {
        templates = repository.templates();
        List<String> labels = new ArrayList<>();
        int position = 0;
        for (int i = 0; i < templates.size(); i++) {
            ShiftRepository.Template t = templates.get(i);
            labels.add(t.name + "  (" + clock(t.startMinute) + "–" + clock(t.endMinute()) + ")");
            if (t.id == chooseId || (chooseId <= 0 && t.id == selectedTemplateId)) position = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        templateSpinner.setAdapter(adapter);
        if (!labels.isEmpty()) {
            templateSpinner.setSelection(position);
            selectedTemplateId = templates.get(position).id;
            refreshSteps(true);
        }
    }

    private ShiftRepository.Template selectedTemplate() {
        for (ShiftRepository.Template t : templates) if (t.id == selectedTemplateId) return t;
        return null;
    }

    private void refreshSteps(boolean resetTime) {
        ShiftRepository.Template t = selectedTemplate();
        if (t == null || stepsArea == null) return;
        if (resetTime) stepClock = t.startMinute;
        stepTimeView.setText("First time: " + clock(stepClock));
        selectedTemplateView.setText("SHIFT TEMPLATE · " + t.name + " · " + clock(t.startMinute) + "–" + clock(t.endMinute()));
        stepsArea.removeAllViews();
        List<ShiftRepository.Step> rules = repository.steps(t.id);
        if (rules.isEmpty()) {
            stepsArea.addView(text("No tasks in this template yet. Add a task below.", 13, MUTED, false));
        }
        for (ShiftRepository.Step step : rules) {
            LinearLayout box = rowBox();
            LinearLayout summary = new LinearLayout(this);
            summary.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(step.title, 15, WHITE, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            summary.addView(name);
            String time = clock((t.startMinute + step.firstMinute) % 1440) +
                    (step.everyMinute > 0 ? " · Every " + intervalLabel(step.everyMinute) : " · Once") +
                    (step.notifyBeforeMinutes > 0 ? " · " + step.notifyBeforeMinutes + "m early" : "");
            TextView meta = text(time, 11, MUTED, false);
            meta.setPadding(0, dp(5), 0, 0);
            summary.addView(meta);
            box.addView(summary, new LinearLayout.LayoutParams(0, -2, 1f));
            Button delete = button("Remove", SURFACE, MUTED);
            box.addView(delete, new LinearLayout.LayoutParams(dp(82), dp(42)));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Remove template task?")
                    .setMessage("Existing shift schedules will not change.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> {
                        repository.deleteStep(step.id, t.id);
                        refreshSteps(false);
                    }).show());
            stepsArea.addView(box, spaced(0, 7));
        }
    }

    private void addTemplate() {
        try {
            ShiftRepository.Template t = repository.addTemplate(templateName.getText().toString(), newStart, newEnd);
            templateName.setText("");
            refreshTemplates(t.id);
            show("Saved shift template");
        } catch (IllegalArgumentException e) {
            show(e.getMessage());
        }
    }

    private void addStep() {
        ShiftRepository.Template t = selectedTemplate();
        if (t == null) return;
        int offset = ShiftRepository.offsetFromClock(t, stepClock);
        try {
            repository.addStep(t.id, stepName.getText().toString(), offset,
                    INTERVALS[intervalSpinner.getSelectedItemPosition()],
                    prioritySpinner.getSelectedItemPosition(), EARLY[earlySpinner.getSelectedItemPosition()]);
            stepName.setText("");
            refreshSteps(false);
            show("Added to " + t.name);
        } catch (IllegalArgumentException e) {
            show(e.getMessage());
        }
    }

    private void startShift() {
        ShiftRepository.Template t = selectedTemplate();
        if (t == null) return;
        try {
            long time = ShiftRepository.runStartForDate(t, selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
            ShiftRepository.Run run = repository.startShift(t.id, time);
            TaskNotifications.syncAll(this);
            ReminderScheduler.rescheduleAll(this);
            refreshCurrentRun();
            show("Shift scheduled: " + run.label);
        } catch (IllegalArgumentException e) {
            show(e.getMessage());
        }
    }

    private void finishShift(ShiftRepository.Run run) {
        new AlertDialog.Builder(this)
                .setTitle("End " + run.label + "?")
                .setMessage("Unfinished shift tasks and their notifications will be canceled, not marked Done. Completed tasks remain in history.")
                .setNegativeButton("Keep shift", null)
                .setPositiveButton("End shift", (d, w) -> {
                    for (long id : repository.endShift(run.id)) {
                        ReminderScheduler.cancel(this, id);
                        TaskNotifications.cancel(this, id);
                    }
                    refreshCurrentRun();
                }).show();
    }

    private void refreshCurrentRun() {
        currentArea.removeAllViews();
        ShiftRepository.Run run = repository.currentRun();
        if (run == null) {
            currentArea.addView(text("No active shift. Choose a template and date, then start your shift.", 13, MUTED, false));
            return;
        }
        TextView headline = text(run.label + "  ·  " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(run.startsAt)) +
                " – " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(run.endsAt)),
                14, WHITE, true);
        currentArea.addView(headline, spaced(0, 6));
        List<TaskStore.Task> tasks = repository.runTasks(run.id);
        int finished = 0;
        for (TaskStore.Task task : tasks) if (task.done) finished++;
        currentArea.addView(text(finished + " / " + tasks.size() + " tasks completed" +
                (System.currentTimeMillis() < run.startsAt ? " · Upcoming shift" :
                        System.currentTimeMillis() > run.endsAt ? " · Shift time ended" : " · Shift in progress"),
                12, ACCENT, false), spaced(0, 12));
        for (TaskStore.Task task : tasks) {
            LinearLayout row = rowBox();
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(task.title, 14, task.done ? MUTED : WHITE, !task.done);
            details.addView(title);
            String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(task.dueAt)) +
                    " · " + (task.done ? "Done" : System.currentTimeMillis() > task.dueAt ? "Overdue" : "Scheduled");
            details.addView(text(when, 12, task.done ? ACCENT : MUTED, false));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            if (!task.done) {
                Button done = button("Done", ACCENT, BG);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(68), dp(42));
                p.leftMargin = dp(9);
                row.addView(done, p);
                done.setOnClickListener(v -> {
                    try (TaskStore tasksStore = new TaskStore(this)) {
                        TaskStore.Completion completed = tasksStore.complete(task.id);
                        if (completed != null) {
                            ReminderScheduler.cancel(this, task.id);
                            TaskNotifications.cancel(this, task.id);
                        }
                    }
                    refreshCurrentRun();
                });
            }
            currentArea.addView(row, spaced(0, 6));
        }
        Button refresh = button("↻  Refresh task status", SURFACE, WHITE);
        currentArea.addView(refresh, spaced(4, 8));
        refresh.setOnClickListener(v -> refreshCurrentRun());
        Button end = button("End shift · Cancel unfinished tasks", SURFACE, Color.rgb(255, 158, 134));
        currentArea.addView(end, spaced(0, 8));
        end.setOnClickListener(v -> finishShift(run));
    }

    private void pickTime(TextView target, int minute, IntConsumer selected) {
        new TimePickerDialog(this, (view, hour, min) -> {
            int newMinute = hour * 60 + min;
            selected.accept(newMinute);
            // Caller controls the label and selected time.
        }, minute / 60, minute % 60, true).show();
    }

    private void pickShiftDate() {
        new DatePickerDialog(this, (date, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            dateView.setText("Shift date: " + dateLabel());
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String dateLabel() {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(selectedDate.getTime());
    }

    private static String clock(int minute) {
        int m = ((minute % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }
    private static String intervalLabel(int minute) {
        return minute >= 60 ? (minute / 60) + "h" : minute + "m";
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setTextColor(WHITE);
        field.setHintTextColor(MUTED);
        field.setHint(hint);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        return field;
    }

    private Button button(String label, int bg, int fg) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(fg);
        view.setBackground(outline(bg, 11));
        return view;
    }

    private LinearLayout rowBox() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(12), dp(8), dp(8), dp(8));
        container.setBackground(outline(SURFACE, 12));
        return container;
    }

    private GradientDrawable outline(int fill, int round) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(round));
        shape.setStroke(dp(1), BORDER);
        return shape;
    }

    private Spinner spinner(String[] values, int initial) {
        Spinner control = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convert, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convert, parent);
                view.setTextColor(WHITE);
                view.setPadding(dp(9), dp(10), dp(9), dp(10));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        control.setAdapter(adapter);
        control.setSelection(initial);
        control.setBackground(outline(BG, 10));
        return control;
    }

    private LinearLayout panel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(outline(SURFACE, 16));
        return box;
    }

    private LinearLayout.LayoutParams spaced(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private void show(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private void buildScreen() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        setContentView(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(34), dp(18), dp(30));
        root.setFitsSystemWindows(true);
        scroll.addView(root);
        TextView back = text("‹  PERSONAL TASKS", 13, ACCENT, true);
        back.setPadding(0, 0, 0, dp(16));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(text("Shift scheduler", 29, WHITE, true), spaced(0, 6));
        root.addView(text("Separate from personal tasks. Design a shift once, then start it whenever you work.",
                14, MUTED, false), spaced(0, 20));

        root.addView(text("YOUR CURRENT SHIFT", 12, ACCENT, true), spaced(0, 9));
        currentArea = panel();
        root.addView(currentArea, spaced(0, 22));

        root.addView(text("SELECT SHIFT TEMPLATE", 12, ACCENT, true), spaced(0, 9));
        templateSpinner = spinner(new String[]{}, 0);
        root.addView(templateSpinner, spaced(0, 12));
        selectedTemplateView = text("", 12, MUTED, true);
        root.addView(selectedTemplateView, spaced(0, 12));
        templateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < templates.size()) {
                    selectedTemplateId = templates.get(position).id;
                    refreshSteps(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        root.addView(text("TASK RULES IN TEMPLATE", 12, ACCENT, true), spaced(0, 10));
        stepsArea = new LinearLayout(this);
        stepsArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(stepsArea, spaced(0, 10));
        LinearLayout rule = panel();
        root.addView(rule, spaced(0, 20));
        rule.addView(text("ADD SHIFT TASK", 12, MUTED, true), spaced(0, 6));
        stepName = input("What needs doing?");
        rule.addView(stepName, spaced(0, 6));
        stepTimeView = text("", 14, ACCENT, true);
        stepTimeView.setPadding(dp(12), dp(12), dp(12), dp(12));
        stepTimeView.setBackground(outline(BG, 10));
        stepTimeView.setOnClickListener(v -> pickTime(stepTimeView, stepClock, m -> {
            stepClock = m;
            stepTimeView.setText("First time: " + clock(m));
        }));
        rule.addView(stepTimeView, spaced(0, 10));
        rule.addView(text("REPEAT WITHIN THIS SHIFT", 11, MUTED, true));
        intervalSpinner = spinner(new String[]{"Once", "Every 30 minutes", "Every hour",
                "Every 2 hours", "Every 3 hours", "Every 4 hours"}, 0);
        rule.addView(intervalSpinner, spaced(3, 10));
        rule.addView(text("SHOW BEFORE TASK TIME", 11, MUTED, true));
        earlySpinner = spinner(new String[]{"At task time", "5 minutes", "10 minutes",
                "15 minutes", "30 minutes", "60 minutes"}, 0);
        rule.addView(earlySpinner, spaced(3, 10));
        rule.addView(text("PRIORITY", 11, MUTED, true));
        prioritySpinner = spinner(new String[]{"Low", "Normal", "High"}, 1);
        rule.addView(prioritySpinner, spaced(3, 11));
        Button addStep = button("+  Add to shift template", ACCENT, BG);
        rule.addView(addStep, new LinearLayout.LayoutParams(-1, dp(45)));
        addStep.setOnClickListener(v -> addStep());

        root.addView(text("START A SHIFT", 12, ACCENT, true), spaced(0, 9));
        LinearLayout startBox = panel();
        root.addView(startBox, spaced(0, 21));
        dateView = text("Shift date: " + dateLabel(), 14, WHITE, true);
        dateView.setPadding(dp(11), dp(11), dp(11), dp(11));
        dateView.setBackground(outline(BG, 10));
        dateView.setOnClickListener(v -> pickShiftDate());
        startBox.addView(dateView, spaced(0, 11));
        startBox.addView(text("This generates each scheduled task for that shift only. "+
                "Hourly rules stop at shift end. Starting midway skips already-past occurrences.",
                12, MUTED, false), spaced(0, 12));
        Button startButton = button("Start / schedule selected shift", ACCENT, BG);
        startBox.addView(startButton, new LinearLayout.LayoutParams(-1, dp(46)));
        startButton.setOnClickListener(v -> startShift());

        root.addView(text("CREATE CUSTOM SHIFT", 12, ACCENT, true), spaced(0, 9));
        LinearLayout custom = panel();
        root.addView(custom, spaced(0, 22));
        templateName = input("Name, e.g. Early shift");
        custom.addView(templateName, spaced(0, 8));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        custom.addView(timeRow, spaced(0, 10));
        newStartView = text("Start: " + clock(newStart), 14, WHITE, true);
        newStartView.setGravity(Gravity.CENTER);
        newStartView.setPadding(dp(6), dp(12), dp(6), dp(12));
        newStartView.setBackground(outline(BG, 9));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.rightMargin = dp(8);
        timeRow.addView(newStartView, left);
        newStartView.setOnClickListener(v -> pickTime(newStartView, newStart, m -> {
            newStart = m;
            newStartView.setText("Start: " + clock(m));
        }));
        newEndView = text("End: " + clock(newEnd), 14, WHITE, true);
        newEndView.setGravity(Gravity.CENTER);
        newEndView.setPadding(dp(6), dp(12), dp(6), dp(12));
        newEndView.setBackground(outline(BG, 9));
        timeRow.addView(newEndView, new LinearLayout.LayoutParams(0, -2, 1));
        newEndView.setOnClickListener(v -> pickTime(newEndView, newEnd, m -> {
            newEnd = m;
            newEndView.setText("End: " + clock(m));
        }));
        Button create = button("+  Save new template", SURFACE, ACCENT);
        custom.addView(create, new LinearLayout.LayoutParams(-1, dp(46)));
        create.setOnClickListener(v -> addTemplate());

        root.addView(text("Reminders are best-effort Android notifications, not guaranteed exact alarms. " +
                "Don't rely on PinTask alone for time-critical patient care.", 12, MUTED, false));
    }
}
