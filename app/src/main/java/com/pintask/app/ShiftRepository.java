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
    private final Context context;

    public ShiftRepository(Context context) {
        store = new TaskStore(context);
        this.context=context.getApplicationContext();
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
                "priority INTEGER NOT NULL DEFAULT 1, notify_before_min INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '', checklist TEXT NOT NULL DEFAULT '[]')");
        db.execSQL("CREATE TABLE IF NOT EXISTS shift_runs (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER NOT NULL," +
                "label TEXT NOT NULL, started_at INTEGER NOT NULL, ends_at INTEGER NOT NULL," +
                "ended INTEGER NOT NULL DEFAULT 0," +
                "task_group TEXT NOT NULL DEFAULT '', day_label INTEGER NOT NULL DEFAULT 0)");
    }

    static void upgradeTables(SQLiteDatabase db) {
        ensureColumn(db,"shift_runs","day_label","INTEGER NOT NULL DEFAULT 0");
        db.execSQL("UPDATE shift_runs SET day_label=1 WHERE template_id=0 AND label IN ('EZ','Off')");
        ensureColumn(db,"shift_steps","notes","TEXT NOT NULL DEFAULT ''");
        ensureColumn(db,"shift_steps","checklist","TEXT NOT NULL DEFAULT '[]'");
    }
    private static void ensureColumn(SQLiteDatabase db,String table,String name,String definition) {
        try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)) {
            while(c.moveToNext()) if(name.equals(c.getString(1)))return;
        }
        db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+name+" "+definition);
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
                        c.getInt(c.getColumnIndexOrThrow("notify_before_min")),
                        c.getString(c.getColumnIndexOrThrow("notes")),c.getString(c.getColumnIndexOrThrow("checklist"))));
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
            for (Step step : steps(id)) addStep(copy.id,step.title,step.firstMinute,step.everyMinute,step.priority,step.notifyBeforeMinutes,step.notes,step.checklist);
            db.setTransactionSuccessful(); return copy;
        } finally { db.endTransaction(); }
    }

    public void addStep(long templateId, String title, int firstMinute, int everyMinute,
            int priority, int earlyMinute) {
        addStep(templateId,title,firstMinute,everyMinute,priority,earlyMinute,"","[]");
    }
    private void addStep(long templateId,String title,int firstMinute,int everyMinute,int priority,int earlyMinute,String notes,String checklist) {
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
        if (earlyMinute != -2 && earlyMinute != -1 && earlyMinute != 0 && earlyMinute != 5 && earlyMinute != 10 &&
                earlyMinute != 15 && earlyMinute != 30 && earlyMinute != 60)
            throw new IllegalArgumentException("Unsupported reminder lead time");
        ContentValues values = new ContentValues();
        values.put("template_id", templateId);
        values.put("title", text);
        values.put("first_min", firstMinute);
        values.put("every_min", everyMinute);
        values.put("priority", priority);
        values.put("notify_before_min", earlyMinute);
        values.put("notes",notes);values.put("checklist",TaskDetails.reset(checklist));
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

    /** Only a shift actually in progress; future plans never hide tonight's ND. */
    public Run currentRun() {
        long now = System.currentTimeMillis();
        try (Cursor c = store.getReadableDatabase().query("shift_runs", null,
                "ended=0 AND started_at<=? AND ends_at>? AND day_label=0",
                new String[]{""+now,""+now}, null, null, "started_at DESC", null)) {
            while(c.moveToNext()) { Run run=readRun(c); if(TaskGroups.matches(context,run.group)) return run; }
            return null;
        }
    }

    /** Soonest future working shift in the selected job/patient filter. */
    public Run nextRun() {
        try(Cursor c=store.getReadableDatabase().query("shift_runs",null,
                "ended=0 AND day_label=0 AND started_at>?",new String[]{""+System.currentTimeMillis()},null,null,"started_at ASC, _id ASC")) {
            while(c.moveToNext()){Run run=readRun(c);if(TaskGroups.matches(context,run.group))return run;}
        }
        return null;
    }
    private List<java.time.LocalDate> checkedDates(List<java.time.LocalDate> dates) {
        if(dates==null || dates.isEmpty())throw new IllegalArgumentException("Choose at least one date");
        java.util.TreeSet<java.time.LocalDate> unique=new java.util.TreeSet<>(dates);
        if(unique.size()>31)throw new IllegalArgumentException("Choose up to 31 dates at once");
        if(unique.first().isBefore(java.time.LocalDate.now()))throw new IllegalArgumentException("Choose today or a future date");
        return new ArrayList<>(unique);
    }
    /** One transaction: a conflict on any date leaves every selected date unchanged. */
    public List<Run> scheduleDates(List<java.time.LocalDate> dates,String label,boolean allowOverlap) {
        List<java.time.LocalDate> selected=checkedDates(dates);SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            List<Run> result=new ArrayList<>();for(java.time.LocalDate day:selected)result.add(schedule(day,label,allowOverlap));
            db.setTransactionSuccessful();return result;
        } finally {db.endTransaction();}
    }
    /** Batch-schedule a saved custom shift type; conflicts roll back every chosen day. */
    public List<Run> scheduleCustomDates(List<java.time.LocalDate> dates,long templateId,boolean allowOverlap) {
        List<java.time.LocalDate> selected=checkedDates(dates);
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            List<Run> result=new ArrayList<>();
            for(java.time.LocalDate day:selected)result.add(scheduleCustom(day,templateId,allowOverlap));
            db.setTransactionSuccessful();
            return result;
        } finally {db.endTransaction();}
    }
    /** Copies all selected groups on the source date into empty destination days. */
    public List<Run> copyDay(java.time.LocalDate source,List<java.time.LocalDate> dates,boolean allowOverlap) {
        List<java.time.LocalDate> selected=checkedDates(dates);
        if(selected.contains(source))throw new IllegalArgumentException("Choose dates different from the day being copied");
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            List<Run> originals=runsForDate(source);if(originals.isEmpty())throw new IllegalArgumentException("This day has no shifts to copy");
            java.time.ZoneId zone=java.time.ZoneId.systemDefault();long now=System.currentTimeMillis();List<Run> result=new ArrayList<>();
            for(java.time.LocalDate day:selected) {
                List<Run> existing=runsForDate(day);
                for(Run original:originals)for(Run run:existing)if(run.group.equals(original.group) && !run.dayLabel)
                    throw new IllegalArgumentException("A selected day already has shifts for this job/patient. Choose empty days.");
                ContentValues closed=new ContentValues();closed.put("ended",1);
                for(Run original:originals)for(Run run:existing)if(run.group.equals(original.group) && run.dayLabel)db.update("shift_runs",closed,"_id=?",args(run.id));
                for(Run original:originals) {
                    java.time.LocalDateTime oldStart=java.time.Instant.ofEpochMilli(original.startsAt).atZone(zone).toLocalDateTime();
                    java.time.LocalDateTime oldEnd=java.time.Instant.ofEpochMilli(original.endsAt).atZone(zone).toLocalDateTime();
                    int duration=(int)java.time.Duration.between(oldStart,oldEnd).toMinutes();
                    long start=day.atTime(oldStart.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),end=ShiftTimes.atMinuteOffset(start,duration);
                    if(end<=now)throw new IllegalArgumentException("Choose dates where the copied shifts have not ended");
                    if(!original.dayLabel && !allowOverlap)try(Cursor c=db.rawQuery("SELECT _id,task_group FROM shift_runs WHERE ended=0 AND day_label=0 AND started_at<? AND ends_at>?",new String[]{""+end,""+start})) {
                        while(c.moveToNext())if(original.group.equals(c.getString(1))){
                            boolean copied=false;for(Run run:result)if(run.id==c.getLong(0))copied=true;
                            // Preserve overlaps already present in the source; warn only about other existing days.
                            if(!copied)throw new IllegalArgumentException("This overlaps another scheduled shift.");
                        }
                    }
                    ContentValues values=new ContentValues();values.put("template_id",original.templateId);values.put("label",original.dayLabel?"Off":original.label);values.put("task_group",original.group);values.put("started_at",start);values.put("ends_at",end);values.put("day_label",original.dayLabel?1:0);
                    long id=db.insertOrThrow("shift_runs",null,values);Run copy=findRun(id);result.add(copy);
                    List<TaskStore.Task> tasks=runTasks(original.id);
                    if(tasks.size()>120)throw new IllegalArgumentException("Maximum 120 tasks per copied shift");
                    for(TaskStore.Task task:tasks) {
                        long due=0;if(task.dueAt>0){int offset=(int)java.time.Duration.between(oldStart,java.time.Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalDateTime()).toMinutes();if(offset<0 || offset>=duration)throw new IllegalArgumentException("A source task falls outside its shift. Adjust its time first.");due=ShiftTimes.atMinuteOffset(start,offset);if(due<=now)continue;}
                        long taskId=TaskStore.insert(db,task.title,due==0?Math.max(now,start):now,task.priority,due,0,task.notifyBeforeMinutes,id,copy.label);
                        store.setGroup(taskId,copy.group);store.saveDetails(taskId,task.notes,TaskDetails.reset(task.checklist),false);
                    }
                }
            }
            db.setTransactionSuccessful();return result;
        } finally {db.endTransaction();}
    }

    public List<Run> runsForDate(java.time.LocalDate date) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<Run> result = new ArrayList<>();
        try (Cursor c = store.getReadableDatabase().query("shift_runs", null,
                "ended=0 AND started_at>=? AND started_at<?", new String[]{""+start,""+end},
                null, null, "started_at ASC")) {
            while (c.moveToNext()) { Run run=readRun(c); if(TaskGroups.matches(context,run.group)) result.add(run); }
        }
        return result;
    }

    public Run findRun(long id) {
        try (Cursor c = store.getReadableDatabase().query("shift_runs", null,
                "_id=? AND ended=0", args(id), null, null, null)) {
            return c.moveToFirst() ? readRun(c) : null;
        }
    }

    /** Legacy preset API kept for existing imports and tests; new UI uses custom types. */
    public Run schedule(java.time.LocalDate date,String label) {
        return schedule(date,label,false);
    }
    public Run schedule(java.time.LocalDate date,String label,boolean allowOverlap) {
        int start, duration;
        switch (label) {
            case "TD": case "TD1": start=480; duration=720; break;
            case "TD2": start=540; duration=720; break;
            case "ND": start=1200; duration=720; break;
            case "Off": start=0; duration=1440; break;
            default: throw new IllegalArgumentException("Choose a shift");
        }
        return insertRun(date,label,start,duration,0,allowOverlap);
    }

    /** Schedule custom hours without automatically adding any task rules. */
    public Run scheduleCustom(java.time.LocalDate date,long templateId,boolean allowOverlap) {
        Template template=findTemplate(templateId);
        if(template==null)throw new IllegalArgumentException("Choose a saved custom shift");
        long start=date.atTime(template.startMinute/60,template.startMinute%60).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(ShiftTimes.atMinuteOffset(start,template.durationMinutes)<=System.currentTimeMillis())
            throw new IllegalArgumentException("Choose a shift that has not ended");
        return insertRun(date,template.name,template.startMinute,template.durationMinutes,template.id,allowOverlap);
    }

    private Run insertRun(java.time.LocalDate date,String label,int start,int duration,long templateId,boolean allowOverlap) {
        long starts=date.atTime(start/60,start%60).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long ends=ShiftTimes.atMinuteOffset(starts,duration);
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            List<Run> existing=runsForDate(date);
            existing.removeIf(run->!run.group.equals(TaskGroups.newTaskGroup(context)));
            boolean marker=label.equals("Off");
            for(Run run:existing) {
                if((run.label.equals(label) || ((run.label.equals("TD") || run.label.equals("TD1")) && (label.equals("TD") || label.equals("TD1")))) && run.startsAt==starts && run.endsAt==ends && run.dayLabel==marker) {
                    db.setTransactionSuccessful();return run;
                }
            }
            if(marker)for(Run run:existing)if(!run.dayLabel)
                throw new IllegalArgumentException("This day has a working shift. End it first before marking no shift.");
            if(!marker && !allowOverlap) {
                try(Cursor c=db.rawQuery("SELECT _id FROM shift_runs WHERE ended=0 AND day_label=0 AND started_at<? AND ends_at>?",new String[]{""+ends,""+starts})) {
                    while(c.moveToNext())if(findRun(c.getLong(0)).group.equals(TaskGroups.newTaskGroup(context)))
                        throw new IllegalArgumentException("This overlaps another scheduled shift.");
                }
            }
            ContentValues closed=new ContentValues();closed.put("ended",1);
            for(Run run:existing)if(run.dayLabel)db.update("shift_runs",closed,"_id=?",args(run.id));
            ContentValues values=new ContentValues();
            values.put("template_id",templateId);values.put("label",label);values.put("day_label",marker?1:0);
            values.put("task_group",TaskGroups.newTaskGroup(context));
            values.put("started_at",starts);values.put("ends_at",ends);
            long id=db.insertOrThrow("shift_runs",null,values);
            db.setTransactionSuccessful();
            return findRun(id);
        } finally {db.endTransaction();}
    }

    /** Explicit occurrences stop at the shift end, including the morning after an ND. */
    public void addTask(long runId, String title, int clockMinute, int every, int early) {
        String name=title==null ? "" : title.trim();
        if(name.isEmpty()) throw new IllegalArgumentException("Enter a task name");
        if(clockMinute < -1 || clockMinute >=1440) throw new IllegalArgumentException("Invalid time");
        if(every!=0 && every!=30 && every!=60 && every!=120 && every!=180 && every!=240)
            throw new IllegalArgumentException("Unsupported repeat interval");
        if(early!=-2 && early!=0 && early!=5 && early!=10 && early!=15 && early!=30 && early!=60)
            throw new IllegalArgumentException("Unsupported reminder");
        if(clockMinute<0 && (every!=0 || early!=-2)) throw new IllegalArgumentException("Choose a time for reminders or repeats");
        SQLiteDatabase db=store.getWritableDatabase(); db.beginTransaction();
        try {
            Run run=findRun(runId);
            long now=System.currentTimeMillis();
            if(run==null || run.endsAt<=now || run.dayLabel)
                throw new IllegalArgumentException("Choose a working shift that has not ended");
            List<Long> times=new ArrayList<>();
            if(clockMinute<0) times.add(0L);
            else {
                java.time.ZoneId zone=java.time.ZoneId.systemDefault();
                java.time.LocalDateTime start=java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(run.startsAt),zone);
                java.time.LocalDateTime end=java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(run.endsAt),zone);
                int duration=(int)java.time.Duration.between(start,end).toMinutes();
                int offset=(clockMinute-start.getHour()*60-start.getMinute()+1440)%1440;
                for(int minute:ShiftTimes.occurrenceOffsets(offset,every,duration)) {
                    long due=ShiftTimes.atMinuteOffset(run.startsAt,minute);
                    if(due<=now) throw new IllegalArgumentException("Choose a future time inside this shift");
                    if(!times.contains(due)) times.add(due);
                }
            }
            for(long due:times) { long id=TaskStore.insert(db,name.substring(0,Math.min(160,name.length())),
                    due==0 ? Math.max(now,run.startsAt) : now,1,due,0,early,run.id,run.label); store.setGroup(id,run.group); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static Run readRun(Cursor c) {
        Run run=new Run(c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getLong(c.getColumnIndexOrThrow("template_id")),
                c.getString(c.getColumnIndexOrThrow("label")),
                c.getLong(c.getColumnIndexOrThrow("started_at")),
                c.getLong(c.getColumnIndexOrThrow("ends_at")));
        run.group=c.getString(c.getColumnIndexOrThrow("task_group"));run.dayLabel=c.getInt(c.getColumnIndexOrThrow("day_label"))==1; return run;
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
        if (startsAt <= 0) throw new IllegalArgumentException("Invalid shift start");
        long endsAt = ShiftTimes.atMinuteOffset(startsAt, template.durationMinutes);
        if (endsAt < System.currentTimeMillis())
            throw new IllegalArgumentException("This shift has already ended. Choose another date");
        try (Cursor c=store.getReadableDatabase().rawQuery(
                "SELECT _id FROM shift_runs WHERE ended=0 AND day_label=0 AND started_at<? AND ends_at>?",
                new String[]{""+endsAt,""+startsAt})) {
            if(c.moveToFirst()) throw new IllegalArgumentException("This overlaps another scheduled shift");
        }
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
            values.put("task_group",TaskGroups.newTaskGroup(context));
            values.put("label", template.name);
            values.put("started_at", startsAt);
            values.put("ends_at", endsAt);
            runId = db.insertOrThrow("shift_runs", null, values);
            for (Occurrence occurrence : planned) {
                long taskId=TaskStore.insert(db, occurrence.rule.title, occurrence.dueAt == 0 ? Math.max(now, startsAt) : now, occurrence.rule.priority,
                        occurrence.dueAt, TaskStore.REPEAT_NEVER,
                        occurrence.rule.notifyBeforeMinutes, runId, template.name);
                store.setGroup(taskId,TaskGroups.newTaskGroup(context));
                store.saveDetails(taskId,occurrence.rule.notes,TaskDetails.reset(occurrence.rule.checklist),false);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new Run(runId, template.id, template.name, startsAt, endsAt);
    }

    /** Snapshot actual occurrences, including completed tasks, without altering the original shift. */
    public Template saveRunAsTemplate(long runId,String name) {
        Run run=findRun(runId);
        if(run==null || run.dayLabel)throw new IllegalArgumentException("Choose a working shift");
        List<TaskStore.Task> tasks=runTasks(runId);
        if(tasks.isEmpty())throw new IllegalArgumentException("Add tasks to this shift first");
        if(tasks.size()>120)throw new IllegalArgumentException("A template supports up to 120 tasks");
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        java.time.LocalDateTime start=java.time.Instant.ofEpochMilli(run.startsAt).atZone(zone).toLocalDateTime();
        java.time.LocalDateTime end=java.time.Instant.ofEpochMilli(run.endsAt).atZone(zone).toLocalDateTime();
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            Template template=addTemplate(name,start.getHour()*60+start.getMinute(),end.getHour()*60+end.getMinute());
            for(TaskStore.Task task:tasks) {
                int offset=task.dueAt==0?-1:(int)java.time.Duration.between(start,java.time.Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalDateTime()).toMinutes();
                addStep(template.id,task.title,offset,0,task.priority,task.notifyBeforeMinutes,task.notes,task.checklist);
            }
            db.setTransactionSuccessful();return template;
        } finally {db.endTransaction();}
    }

    public void applyTemplate(long runId,long templateId) {
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            Run run=findRun(runId);Template template=findTemplate(templateId);
            if(run==null || run.dayLabel || run.endsAt<=System.currentTimeMillis())throw new IllegalArgumentException("Choose a working shift that has not ended");
            if(template==null)throw new IllegalArgumentException("Template not found");
            java.time.ZoneId zone=java.time.ZoneId.systemDefault();
            java.time.LocalDateTime start=java.time.Instant.ofEpochMilli(run.startsAt).atZone(zone).toLocalDateTime();
            java.time.LocalDateTime end=java.time.Instant.ofEpochMilli(run.endsAt).atZone(zone).toLocalDateTime();
            int duration=(int)java.time.Duration.between(start,end).toMinutes();
            if(duration!=template.durationMinutes || start.getHour()*60+start.getMinute()!=template.startMinute)
                throw new IllegalArgumentException("Choose a template with the same shift hours");
            int count=0;long now=System.currentTimeMillis();
            for(Step step:steps(templateId)) {
                List<Integer> offsets=step.firstMinute<0?java.util.Collections.singletonList(-1):ShiftTimes.occurrenceOffsets(step.firstMinute,step.everyMinute,duration);
                for(int offset:offsets) {
                    long due=offset<0?0:ShiftTimes.atMinuteOffset(run.startsAt,offset);
                    if(due!=0 && due<=now)continue;
                    if(++count>120)throw new IllegalArgumentException("Maximum 120 tasks per template");
                    long id=TaskStore.insert(db,step.title,due==0?Math.max(now,run.startsAt):now,step.priority,due,0,step.notifyBeforeMinutes,run.id,run.label);
                    store.setGroup(id,run.group);store.saveDetails(id,step.notes,TaskDetails.reset(step.checklist),false);
                }
            }
            if(count==0)throw new IllegalArgumentException("No future tasks remain in this template");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
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
        public final String notes,checklist;
        Step(long id, long templateId, String title, int firstMinute, int everyMinute,
                int priority, int notifyBeforeMinutes) {
            this(id,templateId,title,firstMinute,everyMinute,priority,notifyBeforeMinutes,"","[]");
        }
        Step(long id,long templateId,String title,int firstMinute,int everyMinute,int priority,int notifyBeforeMinutes,String notes,String checklist) {
            this.notes=notes;this.checklist=checklist;
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
        public String group="";
        public boolean dayLabel;
        Run(long id, long templateId, String label, long startsAt, long endsAt) {
            this.id = id; this.templateId = templateId; this.label = label;
            this.startsAt = startsAt; this.endsAt = endsAt;
        }
    }
}
