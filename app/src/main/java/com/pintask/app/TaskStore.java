package com.pintask.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Durable local SQLite state. Upgrades existing v1 databases without deleting tasks. */
public final class TaskStore extends SQLiteOpenHelper {
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_HIGH = 2;
    public static final int REPEAT_NEVER = 0;
    public static final int REPEAT_DAILY = 1;
    public static final int REPEAT_WEEKLY = 2;
    public static final int REPEAT_MONTHLY = 3;

    public TaskStore(Context context) {
        super(context.getApplicationContext(), "pintask.db", null, 4);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "done INTEGER NOT NULL DEFAULT 0," +
                "completed_at INTEGER NOT NULL DEFAULT 0," +
                "priority INTEGER NOT NULL DEFAULT 1," +
                "due_at INTEGER NOT NULL DEFAULT 0," +
                "recurrence INTEGER NOT NULL DEFAULT 0," +
                "reminded_at INTEGER NOT NULL DEFAULT 0," +
                "notify_before_min INTEGER NOT NULL DEFAULT -1," +
                "shift_run_id INTEGER NOT NULL DEFAULT 0," +
                "shift_label TEXT NOT NULL DEFAULT '')");
        ShiftRepository.createTables(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE tasks ADD COLUMN due_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminded_at INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            // Existing schedules preserve v1.1 behavior: shown immediately, with a due-time alert.
            db.execSQL("ALTER TABLE tasks ADD COLUMN notify_before_min INTEGER NOT NULL DEFAULT -1");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN shift_run_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN shift_label TEXT NOT NULL DEFAULT ''");
            ShiftRepository.createTables(db);
        }
    }

    public synchronized Task add(String title) {
        return add(title, PRIORITY_NORMAL, 0, REPEAT_NEVER, -1);
    }

    public synchronized Task add(String title, int priority, long dueAt, int recurrence) {
        return add(title, priority, dueAt, recurrence, -1);
    }

    /** -1 = post immediately; 0/5/10/15/30/60 = post that many minutes before due. */
    public synchronized Task add(String title, int priority, long dueAt, int recurrence, int notifyBeforeMinutes) {
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Task title is required");
        if (cleaned.length() > 160) cleaned = cleaned.substring(0, 160);
        if (priority < PRIORITY_LOW || priority > PRIORITY_HIGH) priority = PRIORITY_NORMAL;
        if (recurrence < REPEAT_NEVER || recurrence > REPEAT_MONTHLY) recurrence = REPEAT_NEVER;
        if (dueAt <= 0) notifyBeforeMinutes = -1;
        if (notifyBeforeMinutes != -1 && notifyBeforeMinutes != 0 && notifyBeforeMinutes != 5 &&
                notifyBeforeMinutes != 10 && notifyBeforeMinutes != 15 &&
                notifyBeforeMinutes != 30 && notifyBeforeMinutes != 60) {
            throw new IllegalArgumentException("Unsupported early reminder time");
        }
        if (recurrence != REPEAT_NEVER && dueAt <= 0) {
            throw new IllegalArgumentException("Recurring tasks need a date and time");
        }
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        long id = insert(db, cleaned, now, priority, dueAt, recurrence, notifyBeforeMinutes, 0, "");
        return new Task(id, cleaned, now, false, 0, priority, dueAt, recurrence, 0, notifyBeforeMinutes, 0, "");
    }

    static long insert(SQLiteDatabase db, String title, long now, int priority,
            long dueAt, int recurrence, int notifyBeforeMinutes, long shiftRunId, String shiftLabel) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("created_at", now);
        values.put("done", 0);
        values.put("priority", priority);
        values.put("due_at", dueAt);
        values.put("recurrence", recurrence);
        values.put("reminded_at", 0);
        values.put("notify_before_min", notifyBeforeMinutes);
        values.put("shift_run_id", shiftRunId);
        values.put("shift_label", shiftLabel);
        return db.insertOrThrow("tasks", null, values);
    }

    public synchronized Task updateTiming(long id, long dueAt, int recurrence, int early) {
        Task task = find(id);
        if (task == null || task.done) throw new IllegalArgumentException("Task is no longer active");
        if (dueAt < 0 || (dueAt > 0 && dueAt <= System.currentTimeMillis()))
            throw new IllegalArgumentException("Choose a future time");
        long availableAt = task.createdAt;
        if (task.shiftRunId > 0) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT started_at, ends_at FROM shift_runs WHERE _id=? AND ended=0",
                    new String[]{Long.toString(task.shiftRunId)})) {
                if (!c.moveToFirst() || (dueAt > 0 && (dueAt < c.getLong(0) || dueAt >= c.getLong(1))))
                    throw new IllegalArgumentException("Choose a time inside this shift");
                if (dueAt == 0) availableAt = Math.max(System.currentTimeMillis(), c.getLong(0));
            }
        }
        ContentValues values = new ContentValues();
        values.put("due_at", dueAt);
        if (task.shiftRunId > 0 && dueAt == 0) values.put("created_at", availableAt);
        values.put("recurrence", dueAt == 0 || task.shiftRunId > 0 ? REPEAT_NEVER : recurrence);
        values.put("notify_before_min", dueAt == 0 ? -1 : early);
        values.put("reminded_at", 0);
        getWritableDatabase().update("tasks", values, "_id=? AND done=0", new String[]{Long.toString(id)});
        return find(id);
    }

    /** Idempotently completes the task, optionally creating its next occurrence. */
    public synchronized Completion complete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Task original = findIn(db, id);
            if (original == null || original.done) {
                db.setTransactionSuccessful();
                return null;
            }
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("done", 1);
            values.put("completed_at", now);
            int updated = db.update("tasks", values, "_id=? AND done=0", new String[]{Long.toString(id)});
            if (updated == 0) { // May have been canceled by ending a shift.
                db.setTransactionSuccessful();
                return null;
            }
            Task next = null;
            if (original.recurrence != REPEAT_NEVER && original.dueAt > 0) {
                long nextDue = advancePast(original.dueAt, original.recurrence, now);
                long nextId = insert(db, original.title, now, original.priority,
                        nextDue, original.recurrence, original.notifyBeforeMinutes, 0, "");
                next = new Task(nextId, original.title, now, false, 0, original.priority,
                        nextDue, original.recurrence, 0, original.notifyBeforeMinutes, 0, "");
            }
            db.setTransactionSuccessful();
            return new Completion(original, next);
        } finally {
            db.endTransaction();
        }
    }

    /** Keep the local wall clock time across DST and monthly date rollovers. */
    private static long advancePast(long previous, int recurrence, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(previous);
        int field = recurrence == REPEAT_DAILY ? Calendar.DAY_OF_MONTH :
                recurrence == REPEAT_WEEKLY ? Calendar.WEEK_OF_YEAR : Calendar.MONTH;
        do {
            calendar.add(field, 1);
        } while (calendar.getTimeInMillis() <= now);
        return calendar.getTimeInMillis();
    }

    /** Records one reminder per occurrence, even when an alarm is delivered again. */
    public synchronized Task claimDueReminder(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Task task = findIn(db, id);
            if (task == null || task.done || (task.dueAt <= 0 && task.shiftRunId == 0) ||
                    task.notificationAt() > System.currentTimeMillis() || task.remindedAt != 0) {
                db.setTransactionSuccessful();
                return null;
            }
            ContentValues values = new ContentValues();
            values.put("reminded_at", System.currentTimeMillis());
            db.update("tasks", values, "_id=? AND done=0 AND reminded_at=0",
                    new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
            return task;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized Task find(long id) {
        return findIn(getReadableDatabase(), id);
    }

    private static Task findIn(SQLiteDatabase db, long id) {
        try (Cursor cursor = db.query("tasks", null, "_id=?", new String[]{Long.toString(id)},
                null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    public synchronized List<Task> active() {
        return query("done=0", "priority DESC, CASE WHEN due_at=0 THEN 1 ELSE 0 END, due_at ASC, created_at DESC", 1000);
    }

    public synchronized List<Task> activePersonal() {
        return query("done=0 AND shift_run_id=0", "priority DESC, CASE WHEN due_at=0 THEN 1 ELSE 0 END, due_at ASC, created_at DESC", 1000);
    }

    public synchronized List<Task> completedPersonal() {
        return query("done=1 AND shift_run_id=0", "completed_at DESC", 8);
    }

    public synchronized int completedPersonalCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tasks WHERE done=1 AND shift_run_id=0", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized List<Task> completed() {
        return query("done=1", "completed_at DESC", 8);
    }

    public synchronized int completedCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tasks WHERE done=1", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private List<Task> query(String where, String order, int limit) {
        List<Task> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tasks", null, where,
                null, null, null, order, Integer.toString(limit))) {
            while (cursor.moveToNext()) result.add(read(cursor));
        }
        return result;
    }

    static Task read(Cursor cursor) {
        return new Task(
                cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("done")) != 0,
                cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                cursor.getLong(cursor.getColumnIndexOrThrow("due_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("recurrence")),
                cursor.getLong(cursor.getColumnIndexOrThrow("reminded_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("notify_before_min")),
                cursor.getLong(cursor.getColumnIndexOrThrow("shift_run_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("shift_label")));
    }

    public static final class Task {
        public final long id;
        public final String title;
        public final long createdAt;
        public final boolean done;
        public final long completedAt;
        public final int priority;
        public final long dueAt;
        public final int recurrence;
        public final long remindedAt;
        /** -1 means pinned immediately; non-negative means deferred until due minus N minutes. */
        public final int notifyBeforeMinutes;
        public final long shiftRunId;
        public final String shiftLabel;

        Task(long id, String title, long createdAt, boolean done, long completedAt,
                int priority, long dueAt, int recurrence, long remindedAt, int notifyBeforeMinutes,
                long shiftRunId, String shiftLabel) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.done = done;
            this.completedAt = completedAt;
            this.priority = priority;
            this.dueAt = dueAt;
            this.recurrence = recurrence;
            this.remindedAt = remindedAt;
            this.notifyBeforeMinutes = notifyBeforeMinutes;
            this.shiftRunId = shiftRunId;
            this.shiftLabel = shiftLabel;
        }

        public long notificationAt() {
            if (dueAt == 0 && shiftRunId > 0) return createdAt;
            return dueAt - Math.max(0, notifyBeforeMinutes) * 60_000L;
        }
    }

    public static final class Completion {
        public final Task oldTask;
        public final Task nextTask;
        Completion(Task oldTask, Task nextTask) {
            this.oldTask = oldTask;
            this.nextTask = nextTask;
        }
    }
}
