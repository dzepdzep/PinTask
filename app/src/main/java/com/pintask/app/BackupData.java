package com.pintask.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Portable, versioned data only; imported files never supply SQL or filesystem paths. */
final class BackupData {
    static final String[] TABLES = {"tasks","shift_templates","shift_steps","shift_runs","favorites"};
    static final Set<String> TEXT = new HashSet<>(Arrays.asList("title","name","label","shift_label"));
    static String export(TaskStore store) throws Exception {
        SQLiteDatabase db=store.getReadableDatabase(); db.beginTransaction();
        try {
            JSONObject root=new JSONObject(); root.put("format","PinTask"); root.put("version",1);
            for (String table:TABLES) {
                JSONArray rows=new JSONArray();
                try (Cursor c=db.query(table,null,null,null,null,null,"_id")) {
                    while(c.moveToNext()) {
                        JSONObject row=new JSONObject();
                        for(int i=0;i<c.getColumnCount();i++) row.put(c.getColumnName(i),c.getType(i)==Cursor.FIELD_TYPE_STRING ? c.getString(i) : c.getLong(i));
                        rows.put(row);
                    }
                }
                root.put(table,rows);
            }
            db.setTransactionSuccessful(); return root.toString(2);
        } finally { db.endTransaction(); }
    }

    static JSONObject parse(String text) throws Exception {
        if(text.length()>10_000_000) throw new IllegalArgumentException("Backup is too large");
        JSONObject root=new JSONObject(text);
        if(!"PinTask".equals(root.getString("format")) || root.getInt("version")!=1)
            throw new IllegalArgumentException("Unsupported backup format");
        int total=0;
        for(String table:TABLES) total+=root.getJSONArray(table).length();
        if(total>100_000) throw new IllegalArgumentException("Backup has too many records");
        return root;
    }

    static void restore(TaskStore store,String text) throws Exception {
        JSONObject root=parse(text);
        SQLiteDatabase db=store.getWritableDatabase();
        // Validate every row before making any changes; then enforce references in the transaction.
        List<List<ContentValues>> data=new ArrayList<>();
        for(String table:TABLES) {
            List<String> columns=new ArrayList<>();
            try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)) { while(c.moveToNext()) columns.add(c.getString(1)); }
            List<ContentValues> rows=new ArrayList<>();
            JSONArray input=root.getJSONArray(table);
            for(int i=0;i<input.length();i++) {
                JSONObject row=input.getJSONObject(i);
                if(row.length()!=columns.size()) throw new IllegalArgumentException("Unexpected fields in "+table);
                ContentValues values=new ContentValues();
                for(String column:columns) {
                    Object value=row.get(column);
                    if(TEXT.contains(column)) {
                        if(!(value instanceof String) || ((String)value).length()>160)
                            throw new IllegalArgumentException("Invalid text in backup");
                        if(!column.equals("shift_label") && ((String)value).trim().isEmpty())
                            throw new IllegalArgumentException("Empty task or shift name");
                        values.put(column,(String)value);
                    } else {
                        if(!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException("Invalid number in backup");
                        long number=((Number)value).longValue();
                        if(number<0 && !((column.equals("first_min") || column.equals("notify_before_min")) && number==-1))
                            throw new IllegalArgumentException("Invalid negative value");
                        values.put(column,number);
                    }
                }
                if(values.getAsLong("_id")<=0 || values.getAsLong("_id")>=999_999_999) throw new IllegalArgumentException("Invalid record ID");
                range(values,"priority",0,2); range(values,"done",0,2); range(values,"recurrence",0,3);
                range(values,"ended",0,1); range(values,"start_min",0,1439); range(values,"duration_min",1,1440);
                range(values,"first_min",-1,1439);
                allowed(values,"every_min",new long[]{0,30,60,120,180,240});
                allowed(values,"notify_before_min",new long[]{-1,0,5,10,15,30,60});
                if(table.equals("tasks") && values.getAsLong("recurrence")>0 && values.getAsLong("due_at")==0)
                    throw new IllegalArgumentException("Repeating task needs a time");
                if(table.equals("shift_steps") && values.getAsLong("first_min")==-1 && values.getAsLong("every_min")!=0)
                    throw new IllegalArgumentException("Untimed shift task cannot repeat");
                rows.add(values);
            }
            data.add(rows);
        }
        db.beginTransaction();
        try {
            for(String table:TABLES) db.delete(table,null,null);
            for(int i=0;i<TABLES.length;i++) for(ContentValues row:data.get(i)) db.insertOrThrow(TABLES[i],null,row);
            rejectRows(db,"SELECT s._id FROM shift_steps s LEFT JOIN shift_templates t ON s.template_id=t._id WHERE t._id IS NULL OR s.first_min>=t.duration_min");
            rejectRows(db,"SELECT r._id FROM shift_runs r LEFT JOIN shift_templates t ON r.template_id=t._id WHERE t._id IS NULL OR r.ends_at<=r.started_at");
            rejectRows(db,"SELECT a._id FROM tasks a LEFT JOIN shift_runs r ON a.shift_run_id=r._id WHERE a.shift_run_id>0 AND r._id IS NULL");
            rejectRows(db,"SELECT _id FROM shift_runs WHERE ended=0 LIMIT 1 OFFSET 1");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static void range(ContentValues values,String name,long min,long max) {
        if(values.containsKey(name) && (values.getAsLong(name)<min || values.getAsLong(name)>max))
            throw new IllegalArgumentException("Invalid "+name);
    }
    private static void allowed(ContentValues values,String name,long[] allowed) {
        if(!values.containsKey(name)) return;
        for(long value:allowed) if(values.getAsLong(name)==value) return;
        throw new IllegalArgumentException("Invalid "+name);
    }
    private static void rejectRows(SQLiteDatabase db,String query) {
        try(Cursor c=db.rawQuery(query,null)) { if(c.moveToFirst()) throw new IllegalArgumentException("Backup contains inconsistent shift records"); }
    }
}
