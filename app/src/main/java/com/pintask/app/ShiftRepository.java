package com.pintask.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Reusable shift templates with bounded, per-shift one-shot tasks. */
public final class ShiftRepository implements AutoCloseable {
    private final TaskStore store;

    public ShiftRepository(Context context) {
        store = new TaskStore(context);
    }

    static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS shift_templates (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL, start_min INTEGER NOT NULL," +
                "duration_min INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS shift_steps (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "template_id INTEGER NOT NULL, title TEXT NOT NULL," +
                "first_min INTEGER NOT NULL, every_min INTEGER NOT NULL DEFAULT 0," +
                "priority INTEGER NOT NULL DEFAULT 1, notify_before_min INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS shift_runs (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER NOT NULL," +
                "label TEXT NOT NULL, started_at INTEGER NOT NULL, ends_at INTEGER NOT NULL," +
                "ended INTEGER NOT NULL DEFAULT 0)");
    }

    private static String[] args(long id) { return new String[]{Long.toString(id)}; }

    public List<Template> templates() {
        List<Template> result = new ArrayList<>();
        try (Cursor c = store.getReadableDatabase().query("shift_templates", null, null,
                null, null, null, "_id ASC")) {
            while (c.moveToNext()) {
                result.add(new Template(c.getLong(c.getColumnIndexOrThrow("_id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getInt(c.getColumnIndexOrThrow("start_min")),
                        c.getInt(c.getColumnIndexOrThrow("duration_min"))));
            }
        }
        return result;
    }

    public Template findTemplate(long id) {
        try (Cursor c = store.getReadableDatabase().query("shift_templates", null, "_id=?",
                args(id), null, null, null)) {
            if (c.moveToFirst()) return new Template(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3));
            return null;
        }
    }

    public Template addTemplate(String name, int startMinute, int endMinute) {
        String title = name == null ? "" : name.trim();
        if (title.isEmpty()) throw new IllegalArgumentException("Enter a shift name");
        if (title.length() > 50) title = title.substring(0, 50);
        if (startMinute < 0 || startMinute >= 1440 || endMinute < 0 || endMinute >= 1440)
            throw new IllegalArgumentException("Invalid shift time");
        int duration = (endMinute - startMinute + 1440) % 1440;
        if (duration == 0) duration = 1440;
        ContentValues values = new ContentValues();
        values.put("name", title);
        values.put("start_min", startMinute);
        values.put("duration_min", duration);
        long id = store.getWritableDatabase().insertOrThrow("shift_templates", null, values);
        return new Template(id, title, startMinute, duration);
    }

    public List<Step> steps(long templateId) {
        List<Step> result = new ArrayList<>();
        try (Cursor c = store.getReadableDatabase().query("shift_steps", null, "template_id=?",
                args(templateId), null, null, "first_min ASC, _id ASC")) {
            while (c.moveToNext()) {
                result.add(new Step(c.getLong(c.getColumnIndexOrThrow("_id")),
                        c.getLong(c.getColumnIndexOrThrow("template_id")),
                        c.getString(c.getColumnIndexOrThrow("title")),
                        c.getInt(c.getColumnIndexOrThrow("first_min")),
                        c.getInt(c.getColumnIndexOrThrow("every_min")),
                        c.getInt(c.getColumnIndexOrThrow("priority")),
                        c.getInt(c.getColumnIndexOrThrow("notify_before_min"))));
            }
        }
        return result;
    }

    public Template duplicateTemplate(long id,String name) {
        Template source = findTemplate(id);
        if (source == null) throw new IllegalArgumentException("Choose a shift template");
        SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
        try {
            Template copy = addTemplate(name,source.startMinute,source.endMinute());
            for (Step step : steps(id)) addStep(copy.id,step.title,step.firstMinute,step.everyMinute,step.priority,step.notifyBeforeMinutes);
            db.setTransactionSuccessful(); return copy;
        } finally { db.endTransaction(); }
    }

    public void addStep(long templateId, String title, int firstMinute, int everyMinute,
            int priority, int earlyMinute) {
        Template t = findTemplate(templateId);
        if (t == null) throw new IllegalArgumentException("Choose a shift template");
        String text = title == null ? "" : title.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Enter a task title");
        if (text.length() > 160) text = text.substring(0, 160);
        if (firstMinute < -1 || firstMinute >= t.durationMinutes)
            throw new IllegalArgumentException("First task time must be inside the shift");
        if (firstMinute == -1 && everyMinute != 0)
            throw new IllegalArgumentException("Set a first time for repeating tasks");
        if (everyMinute != 0 && everyMinute != 30 && everyMinute != 60 &&
                everyMinute != 120 && everyMinute != 180 && everyMinute != 240)
            throw new IllegalArgumentException("Unsupported interval");
        if (priority < 0 || priority > 2) throw new IllegalArgumentException("Invalid priority");
        if (earlyMinute != 0 && earlyMinute != 5 && earlyMinute != 10 &&
                earlyMinute != 15 && earlyMinute != 30 && earlyMinute != 60)
            throw new IllegalArgumentException("Unsupported reminder lead time");
        ContentValues values = new ContentValues();
        values.put("template_id", templateId);
        values.put("title", text);
        values.put("first_min", firstMinute);
        values.put("every_min", everyMinute);
        values.put("priority", priority);
        values.put("notify_before_min", earlyMinute);
        store.getWritableDatabase().insertOrThrow("shift_steps", null, values);
    }

    public void deleteStep(long id, long templateId) {
        // Remove template rule only. Existing shifts keep their instantiated tasks.
        store.getWritableDatabase().delete("shift_steps", "_id=? AND template_id=?",
                new String[]{Long.toString(id), Long.toString(templateId)});
    }

    public void updateStepTime(Step step, int firstMinute) {
        Template t = findTemplate(step.templateId);
        if (t == null || firstMinute < -1 || firstMinute >= t.durationMinutes)
            throw new IllegalArgumentException("Choose a time inside the shift");
        ContentValues values = new ContentValues();
        values.put("first_min", firstMinute);
        if (firstMinute == -1) values.put("every_min", 0);
        store.getWritableDatabase().update("shift_steps", values, "_id=?", args(step.id));
    }

    public void updateTemplateTimes(long id, int start, int end) {
        if (findTemplate(id) == null || start < 0 || start >= 1440 || end < 0 || end >= 1440)
            throw new IllegalArgumentException("Choose valid shift hours");
        int duration = (end - start + 1440) % 1440;
        if (duration == 0) duration = 1440;
        for (Step step : steps(id)) {
            if (step.firstMinute >= duration)
                throw new IllegalArgumentException("A task falls outside these hours. Change its time first");
        }
        ContentValues values = new ContentValues();
        values.put("start_min", start);
        values.put("duration_min", duration);
        store.getWritableDatabase().update("shift_templates", values, "_id=?", args(id));
    }

    public Run currentRun() {
        try (Cursor c = store.getReadableDatabase().query("shift_runs", null, "ended=0",
                null, null, null, "started_at DESC", "1")) {
            return c.moveToFirst() ? readRun(c) : null;
        }
    }

    private static Run readRun(Cursor c) {
        return new Run(c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getLong(c.getColumnIndexOrThrow("template_id")),
                c.getString(c.getColumnIndexOrThrow("label")),
                c.getLong(c.getColumnIndexOrThrow("started_at")),
                c.getLong(c.getColumnIndexOrThrow("ends_at")));
    }

    public static long runStartForDate(Template template, int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.setLenient(false);
        c.set(year, month, day, template.startMinute / 60, template.startMinute % 60, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** First time of each step uses wall-clock minute offset into the selected shift. */
    public static int offsetFromClock(Template template, int wallMinute) {
        return (wallMinute - template.startMinute + 1440) % 1440;
    }

    public Run startShift(long templateId, long startsAt) {
        Template template = findTemplate(templateId);
        if (template == null) throw new IllegalArgumentException("Shift template not found");
        List<Step> rules = steps(templateId);
        if (rules.isEmpty()) throw new IllegalArgumentException("Add at least one shift task first");
        if (currentRun() != null) throw new IllegalArgumentException("End your current shift first");
        if (startsAt <= 0) throw new IllegalArgumentException("Invalid shift start");
        long endsAt = ShiftTimes.atMinuteOffset(startsAt, template.durationMinutes);
        if (endsAt < System.currentTimeMillis())
            throw new IllegalArgumentException("This shift has already ended. Choose another date");
        // Resolve all occurrences first so invalid templates do not generate partial runs.
        List<Occurrence> planned = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Step rule : rules) {
            if (rule.firstMinute == -1) {
                planned.add(new Occurrence(rule, 0));
                continue;
            }
            for (int minute : ShiftTimes.occurrenceOffsets(rule.firstMinute,
                    rule.everyMinute, template.durationMinutes)) {
                long due = ShiftTimes.atMinuteOffset(startsAt, minute);
                if (due >= now) planned.add(new Occurrence(rule, due));
                if (planned.size() > 120) throw new IllegalArgumentException(
                        "Maximum 120 occurrences per shift. Use longer repeat intervals");
            }
        }
        if (planned.size() > 120) throw new IllegalArgumentException("Maximum 120 tasks per shift");
        if (planned.isEmpty())
            throw new IllegalArgumentException("No future task occurrences remain on this shift");
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        long runId;
        try {
            ContentValues values = new ContentValues();
            values.put("template_id", templateId);
            values.put("label", template.name);
            values.put("started_at", startsAt);
            values.put("ends_at", endsAt);
            runId = db.insertOrThrow("shift_runs", null, values);
            for (Occurrence occurrence : planned) {
                TaskStore.insert(db, occurrence.rule.title, occurrence.dueAt == 0 ? Math.max(now, startsAt) : now, occurrence.rule.priority,
                        occurrence.dueAt, TaskStore.REPEAT_NEVER,
                        occurrence.rule.notifyBeforeMinutes, runId, template.name);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new Run(runId, template.id, template.name, startsAt, endsAt);
    }

    public List<TaskStore.Task> runTasks(long runId) {
        List<TaskStore.Task> result = new ArrayList<>();
        try (Cursor c = store.getReadableDatabase().query("tasks", null,
                "shift_run_id=? AND done IN (0,1)", args(runId), null, null, "due_at ASC, _id ASC")) {
            while (c.moveToNext()) result.add(TaskStore.read(c));
        }
        return result;
    }

    /** Cancel unfinished items without falsely marking them Done. */
    public List<Long> endShift(long runId) {
        List<Long> canceled = new ArrayList<>();
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query("tasks", new String[]{"_id"},
                    "shift_run_id=? AND done=0", args(runId), null, null, null)) {
                while (c.moveToNext()) canceled.add(c.getLong(0));
            }
            ContentValues closed = new ContentValues();
            closed.put("ended", 1);
            db.update("shift_runs", closed, "_id=? AND ended=0", args(runId));
            ContentValues canceledValues = new ContentValues();
            canceledValues.put("done", 2); // Canceled, not completed.
            db.update("tasks", canceledValues, "shift_run_id=? AND done=0", args(runId));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return canceled;
    }

    @Override public void close() { store.close(); }

    private static final class Occurrence {
        final Step rule;
        final long dueAt;
        Occurrence(Step rule, long dueAt) { this.rule = rule; this.dueAt = dueAt; }
    }

    public static final class Template {
        public final long id;
        public final String name;
        public final int startMinute;
        public final int durationMinutes;
        Template(long id, String name, int startMinute, int durationMinutes) {
            this.id = id; this.name = name; this.startMinute = startMinute;
            this.durationMinutes = durationMinutes;
        }
        public int endMinute() { return (startMinute + durationMinutes) % 1440; }
    }

    public static final class Step {
        public final long id;
        public final long templateId;
        public final String title;
        public final int firstMinute;
        public final int everyMinute;
        public final int priority;
        public final int notifyBeforeMinutes;
        Step(long id, long templateId, String title, int firstMinute, int everyMinute,
                int priority, int notifyBeforeMinutes) {
            this.id = id; this.templateId = templateId; this.title = title;
            this.firstMinute = firstMinute; this.everyMinute = everyMinute;
            this.priority = priority; this.notifyBeforeMinutes = notifyBeforeMinutes;
        }
    }

    public static final class Run {
        public final long id;
        public final long templateId;
        public final String label;
        public final long startsAt;
        public final long endsAt;
        Run(long id, long templateId, String label, long startsAt, long endsAt) {
            this.id = id; this.templateId = templateId; this.label = label;
            this.startsAt = startsAt; this.endsAt = endsAt;
        }
    }
}
