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
    public static final int REMINDER_OFF = -2;
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_HIGH = 2;
    public static final int REPEAT_NEVER = 0;
    public static final int REPEAT_DAILY = 1;
    public static final int REPEAT_WEEKLY = 2;
    public static final int REPEAT_MONTHLY = 3;
    public static final int REPEAT_FORTNIGHTLY = 4;
    private final Context context;

    public TaskStore(Context context) {
        super(context.getApplicationContext(), "pintask.db", null, 7);
        this.context=context.getApplicationContext();
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
                "shift_label TEXT NOT NULL DEFAULT ''," +
                "snoozed_until INTEGER NOT NULL DEFAULT 0," +
                "next_task_id INTEGER NOT NULL DEFAULT 0," +
                "task_group TEXT NOT NULL DEFAULT ''," +
                "notes TEXT NOT NULL DEFAULT '', checklist TEXT NOT NULL DEFAULT '[]'," +
                "paused INTEGER NOT NULL DEFAULT 0, repeat_after INTEGER NOT NULL DEFAULT 0, repeat_anchor INTEGER NOT NULL DEFAULT 0)");
        ShiftRepository.createTables(db);
        createFavorites(db);
        createBundles(db);
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
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN snoozed_until INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN next_task_id INTEGER NOT NULL DEFAULT 0");
            createFavorites(db);
        }
        if(oldVersion<6) {
            ensureGroupColumn(db,"tasks"); ensureGroupColumn(db,"shift_runs");
        }
        if(oldVersion<7) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE tasks ADD COLUMN checklist TEXT NOT NULL DEFAULT '[]'");
            db.execSQL("ALTER TABLE tasks ADD COLUMN paused INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_after INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_anchor INTEGER NOT NULL DEFAULT 0");
            createBundles(db);
        }
    }

    static void createBundles(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bundles (_id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,task_group TEXT NOT NULL DEFAULT '',payload TEXT NOT NULL)");
    }

    private static void ensureGroupColumn(SQLiteDatabase db, String table) {
        try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)) {
            while(c.moveToNext()) if("task_group".equals(c.getString(1))) return;
        }
        db.execSQL("ALTER TABLE "+table+" ADD COLUMN task_group TEXT NOT NULL DEFAULT ''");
    }

    public synchronized Task setGroup(long id,String group) {
        ContentValues values=new ContentValues(); values.put("task_group",TaskGroups.clean(group));
        getWritableDatabase().update("tasks",values,"_id=?",new String[]{""+id}); return find(id);
    }

    private static void createFavorites(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, priority INTEGER NOT NULL DEFAULT 1)");
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
        if (!RepeatRules.valid(recurrence)) throw new IllegalArgumentException("Unsupported repeat interval");
        if (dueAt <= 0 && notifyBeforeMinutes != REMINDER_OFF) notifyBeforeMinutes = -1;
        if (notifyBeforeMinutes != REMINDER_OFF && notifyBeforeMinutes != -1 && notifyBeforeMinutes != 0 && notifyBeforeMinutes != 5 &&
                notifyBeforeMinutes != 10 && notifyBeforeMinutes != 15 &&
                notifyBeforeMinutes != 30 && notifyBeforeMinutes != 60) {
            throw new IllegalArgumentException("Unsupported early reminder time");
        }
        if (recurrence != REPEAT_NEVER && dueAt <= 0) {
            throw new IllegalArgumentException("Recurring tasks need a date and time");
        }
        dueAt=RepeatRules.first(dueAt,recurrence);
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        long id = insert(db, cleaned, now, priority, dueAt, recurrence, notifyBeforeMinutes, 0, "");
        return setGroup(id,TaskGroups.newTaskGroup(context));
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
        if(!RepeatRules.valid(recurrence)) throw new IllegalArgumentException("Unsupported repeat interval");
        Task task = find(id);
        if (task == null || task.done) throw new IllegalArgumentException("Task is no longer active");
        if (dueAt < 0 || (dueAt > 0 && dueAt <= System.currentTimeMillis()))
            throw new IllegalArgumentException("Choose a future time");
        dueAt=RepeatRules.first(dueAt,recurrence);
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
        values.put("due_at", dueAt);values.put("repeat_anchor",0);
        if (task.shiftRunId > 0 && dueAt == 0) values.put("created_at", availableAt);
        values.put("snoozed_until", 0);
        values.put("recurrence", dueAt == 0 || task.shiftRunId > 0 ? REPEAT_NEVER : recurrence);
        if(dueAt==0 || recurrence==0 || task.shiftRunId>0){values.put("paused",0);values.put("repeat_after",0);}
        values.put("notify_before_min", dueAt == 0 && early != REMINDER_OFF ? -1 : early);
        values.put("reminded_at", 0);
        getWritableDatabase().update("tasks", values, "_id=? AND done=0", new String[]{Long.toString(id)});
        return find(id);
    }

    /** Repeat creates a new personal task and leaves the completed record intact. */
    public synchronized Task repeat(long id, long dueAt, int recurrence, int early) {
        Task original = find(id);
        if (original == null) throw new IllegalArgumentException("Task no longer exists");
        if (dueAt > 0 && dueAt <= System.currentTimeMillis())
            throw new IllegalArgumentException("Choose a future time");
        Task copy=setGroup(add(original.title, original.priority, dueAt, recurrence, early).id,original.group);
        return saveDetails(copy.id,original.notes,TaskDetails.reset(original.checklist),recurrence!=0 && original.repeatAfter);
    }

    public synchronized Task rename(long id, String title) {
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Enter a task name");
        ContentValues values = new ContentValues();
        values.put("title", cleaned.substring(0, Math.min(160, cleaned.length())));
        getWritableDatabase().update("tasks", values, "_id=?", new String[]{Long.toString(id)});
        return find(id);
    }

    public synchronized Task changePriority(long id, int priority) {
        if (priority < 0 || priority > 2) throw new IllegalArgumentException("Invalid priority");
        ContentValues values = new ContentValues();
        values.put("priority", priority);
        getWritableDatabase().update("tasks", values, "_id=? AND done=0", new String[]{Long.toString(id)});
        return find(id);
    }

    public synchronized Task reopen(long id) {
        Task task = find(id);
        if (task == null || !task.done || task.recurrence != REPEAT_NEVER || task.shiftRunId > 0)
            throw new IllegalArgumentException("Use Repeat as a new task for this item");
        ContentValues values = new ContentValues();
        values.put("done", 0);
        values.put("completed_at", 0);
        values.put("due_at", 0);
        values.put("snoozed_until", 0);
        values.put("next_task_id", 0);
        values.put("notify_before_min", -1);
        values.put("reminded_at", 0);
        getWritableDatabase().update("tasks", values, "_id=? AND done=1", new String[]{Long.toString(id)});
        return find(id);
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("tasks", "_id=?", new String[]{Long.toString(id)});
    }

    public synchronized void stopRepeating(long id) {
        ContentValues values = new ContentValues();
        values.put("recurrence", REPEAT_NEVER);values.put("paused",0);values.put("repeat_after",0);
        getWritableDatabase().update("tasks", values, "_id=? AND done=0", new String[]{Long.toString(id)});
    }

    public synchronized Task snooze(long id, long until) {
        Task task = find(id);
        if (task == null || task.done) throw new IllegalArgumentException("Task is no longer active");
        if (until <= System.currentTimeMillis()) throw new IllegalArgumentException("Choose a future time");
        if (task.shiftRunId > 0) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT started_at,ends_at FROM shift_runs WHERE _id=? AND ended=0",
                    new String[]{Long.toString(task.shiftRunId)})) {
                if (!c.moveToFirst() || until < c.getLong(0) || until >= c.getLong(1))
                    throw new IllegalArgumentException("Snooze must stay inside the shift");
            }
        }
        ContentValues values = new ContentValues();
        values.put("snoozed_until", until);
        values.put("reminded_at", 0);
        getWritableDatabase().update("tasks", values, "_id=? AND done=0", new String[]{Long.toString(id)});
        return find(id);
    }

    public synchronized long saveFavorite(long id) {
        Task task = find(id);
        if (task == null) throw new IllegalArgumentException("Task no longer exists");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id FROM favorites WHERE title=? AND priority=?",
                new String[]{task.title, Integer.toString(task.priority)})) { if (c.moveToFirst()) return c.getLong(0); }
        ContentValues values = new ContentValues();
        values.put("title", task.title); values.put("priority", task.priority);
        return getWritableDatabase().insertOrThrow("favorites", null, values);
    }

    public synchronized List<Task> favorites() {
        List<Task> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id,title,priority FROM favorites ORDER BY title COLLATE NOCASE", null)) {
            while (c.moveToNext()) result.add(new Task(c.getLong(0), c.getString(1), 0, false, 0, c.getInt(2), 0, 0, 0, -1, 0, ""));
        }
        return result;
    }

    public synchronized Task addFavorite(long id) {
        for (Task favorite : favorites()) if (favorite.id == id) return add(favorite.title, favorite.priority, 0, 0, -1);
        throw new IllegalArgumentException("Favorite no longer exists");
    }

    public synchronized void removeFavorite(long id) {
        getWritableDatabase().delete("favorites", "_id=?", new String[]{Long.toString(id)});
    }

    /** Brief undo, including removal of the automatically generated next occurrence. */
    public synchronized long undoCompletion(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Task original = findIn(db, id);
            if (original == null || !original.done || original.completedAt == 0 ||
                    System.currentTimeMillis() - original.completedAt > 10_000)
                throw new IllegalArgumentException("Undo expired. Use the Done menu instead");
            if (original.shiftRunId > 0) {
                try (Cursor c = db.rawQuery("SELECT ended FROM shift_runs WHERE _id=?", new String[]{Long.toString(original.shiftRunId)})) {
                    if (!c.moveToFirst() || c.getInt(0) != 0) throw new IllegalArgumentException("This shift has ended");
                }
            }
            long nextId;
            try (Cursor c = db.rawQuery("SELECT next_task_id FROM tasks WHERE _id=?", new String[]{Long.toString(id)})) {
                c.moveToFirst(); nextId = c.getLong(0);
            }
            if (nextId > 0) {
                Task next = findIn(db, nextId);
                if (next == null || next.done || !next.title.equals(original.title) || next.priority != original.priority ||
                        next.recurrence != original.recurrence || !next.group.equals(original.group) || !next.notes.equals(original.notes) || !next.checklist.equals(TaskDetails.reset(original.checklist)) || next.paused || next.repeatAnchor!=0 || next.repeatAfter!=original.repeatAfter || next.snoozedUntil != 0 || next.notifyBeforeMinutes != original.notifyBeforeMinutes ||
                        next.dueAt != nextDue(original,original.completedAt))
                    throw new IllegalArgumentException("The next occurrence has changed; undo is unavailable");
                db.delete("tasks", "_id=?", new String[]{Long.toString(nextId)});
            }
            ContentValues values = new ContentValues();
            values.put("done", 0); values.put("completed_at", 0); values.put("next_task_id", 0);
            db.update("tasks", values, "_id=? AND done=1", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
            return nextId;
        } finally { db.endTransaction(); }
    }

    /** Idempotently completes the task, optionally creating its next occurrence. */
    public synchronized Completion complete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Task original = findIn(db, id);
            if (original == null || original.done || original.paused) {
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
                long nextDue = nextDue(original,now);
                long nextId = insert(db, original.title, now, original.priority,
                        nextDue, original.recurrence, original.notifyBeforeMinutes, 0, "");
                next = new Task(nextId, original.title, now, false, 0, original.priority,
                        nextDue, original.recurrence, 0, original.notifyBeforeMinutes, 0, "");
                next=setGroup(nextId,original.group);
                next=saveDetails(nextId,original.notes,TaskDetails.reset(original.checklist),original.repeatAfter);
                ContentValues link = new ContentValues();
                link.put("next_task_id", nextId);
                db.update("tasks", link, "_id=?", new String[]{Long.toString(id)});
            }
            db.setTransactionSuccessful();
            return new Completion(original, next);
        } finally {
            db.endTransaction();
        }
    }

    /** Skips without adding a false Done record or creating duplicate tasks. */
    public synchronized Task skipOccurrence(long id) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Task task=findIn(db,id);
            if(task==null || task.done || task.paused || task.shiftRunId>0 || task.dueAt<=0 || task.recurrence==0)
                throw new IllegalArgumentException("Choose an active repeating personal task");
            ContentValues values=new ContentValues();
            values.put("due_at",nextDue(task,System.currentTimeMillis()));values.put("repeat_anchor",0);
            values.put("snoozed_until",0);values.put("reminded_at",0);
            db.update("tasks",values,"_id=? AND done=0",new String[]{""+id});
            db.setTransactionSuccessful();return findIn(db,id);
        }finally{db.endTransaction();}
    }

    static long nextDue(Task task,long now) {
        return RepeatRules.next(task.repeatAfter?now:(task.repeatAnchor>0?task.repeatAnchor:task.dueAt),task.recurrence,task.repeatAfter?now:Math.max(now,task.dueAt));
    }

    public synchronized Task saveDetails(long id,String notes,String checklist,boolean after) {
        if(notes==null || notes.length()>4000)throw new IllegalArgumentException("Notes can contain up to 4000 characters");
        TaskDetails.validate(checklist);
        Task original=find(id);if(original==null)throw new IllegalArgumentException("Task no longer exists");
        if(after && (original.recurrence==0 || original.shiftRunId>0))throw new IllegalArgumentException("Choose a repeating personal task first");
        ContentValues values=new ContentValues();values.put("notes",notes);values.put("checklist",checklist);values.put("repeat_after",after?1:0);
        getWritableDatabase().update("tasks",values,"_id=?",new String[]{""+id});return find(id);
    }

    public synchronized Task pause(long id,boolean paused) {
        Task task=find(id);
        if(task==null || task.done || task.shiftRunId>0 || task.recurrence==0)throw new IllegalArgumentException("Choose an active recurring personal task");
        ContentValues values=new ContentValues();values.put("paused",paused?1:0);
        if(!paused){values.put("snoozed_until",0);values.put("reminded_at",0);}
        if(!paused && task.dueAt<=System.currentTimeMillis()) {
            values.put("due_at",nextDue(task,System.currentTimeMillis()));values.put("snoozed_until",0);values.put("reminded_at",0);
        }
        getWritableDatabase().update("tasks",values,"_id=?",new String[]{""+id});return find(id);
    }

    public synchronized Task tomorrow(long id) {
        Task task=find(id);if(task==null || task.done || task.shiftRunId>0)throw new IllegalArgumentException("Choose an active personal task");
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        java.time.LocalTime time=task.dueAt>0?java.time.Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalTime():java.time.LocalTime.of(9,0);
        long due=java.time.LocalDate.now(zone).plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli();
        ContentValues values=new ContentValues();values.put("due_at",due);if(task.recurrence>0 && task.repeatAnchor==0 && task.dueAt>0)values.put("repeat_anchor",task.dueAt);values.put("snoozed_until",0);values.put("reminded_at",0);
        getWritableDatabase().update("tasks",values,"_id=?",new String[]{""+id});return find(id);
    }

    public synchronized Task undoTomorrow(Task before,long expectedDue) {
        Task current=find(before.id);
        if(current==null || current.done || current.dueAt!=expectedDue || current.recurrence!=before.recurrence || current.snoozedUntil!=0 || current.notifyBeforeMinutes!=before.notifyBeforeMinutes)
            throw new IllegalArgumentException("This task has changed; undo is unavailable");
        ContentValues values=new ContentValues();values.put("due_at",before.dueAt);values.put("repeat_anchor",before.repeatAnchor);values.put("snoozed_until",before.snoozedUntil);values.put("reminded_at",before.remindedAt);
        getWritableDatabase().update("tasks",values,"_id=?",new String[]{""+before.id});return find(before.id);
    }

    /** Records one reminder per occurrence, even when an alarm is delivered again. */
    public synchronized Task claimDueReminder(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Task task = findIn(db, id);
            if (task == null || task.done || !task.reminderEnabled() || (task.dueAt <= 0 && task.shiftRunId == 0 && task.snoozedUntil == 0) ||
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

    public synchronized List<Task> activeAll() {return query("done=0","priority DESC, due_at ASC, _id DESC",Integer.MAX_VALUE);}

    public synchronized List<Task> activePersonal() {
        return query("done=0 AND shift_run_id=0", "priority DESC, CASE WHEN due_at=0 THEN 1 ELSE 0 END, due_at ASC, created_at DESC", 1000);
    }

    public synchronized List<Task> completedAll() {return query("done=1","completed_at DESC, _id DESC",Integer.MAX_VALUE);}

    public synchronized List<Task> completedPersonal() {
        return completedPersonal(20);
    }

    public synchronized List<Task> completedPersonal(int limit) {
        return query("done=1 AND shift_run_id=0", "completed_at DESC, _id DESC", limit);
    }

    public synchronized List<Task> completedShifts(int limit) {
        return query("done=1 AND shift_run_id>0", "completed_at DESC, _id DESC", limit);
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
        Task task = new Task(
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
        task.repeatAnchor=cursor.getLong(cursor.getColumnIndexOrThrow("repeat_anchor"));
        task.notes=cursor.getString(cursor.getColumnIndexOrThrow("notes"));
        task.checklist=cursor.getString(cursor.getColumnIndexOrThrow("checklist"));
        task.paused=cursor.getInt(cursor.getColumnIndexOrThrow("paused"))!=0;
        task.repeatAfter=cursor.getInt(cursor.getColumnIndexOrThrow("repeat_after"))!=0;
        task.group=cursor.getString(cursor.getColumnIndexOrThrow("task_group"));
        task.snoozedUntil = cursor.getLong(cursor.getColumnIndexOrThrow("snoozed_until"));
        return task;
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
        public long snoozedUntil,repeatAnchor;
        public String group="";
        public String notes="", checklist="[]";
        public boolean paused, repeatAfter;

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

        public boolean reminderEnabled() { return !paused && (notifyBeforeMinutes != REMINDER_OFF || snoozedUntil > 0); }

        public long notificationAt() {
            if (snoozedUntil > 0) return snoozedUntil;
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
