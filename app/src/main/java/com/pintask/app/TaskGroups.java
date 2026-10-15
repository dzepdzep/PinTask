package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.widget.EditText;
import android.widget.Toast;
import java.util.*;
import java.util.function.Consumer;

/** Shared selection affects lists and new tasks, never reminder delivery. */
final class TaskGroups {
    static String clean(String name) {
        String value=name==null?"":name.trim();
        if(value.length()>80) throw new IllegalArgumentException("Use a group name up to 80 characters");
        return value;
    }
    static String selected(Context context) { return context.getSharedPreferences("settings",0).getString("group",null); }
    static String newTaskGroup(Context context) { String group=selected(context);return group==null?"":group; }
    static boolean matches(Context context,String group) { String selected=selected(context);return selected==null || selected.equals(group); }
    static String label(Context context) { String group=selected(context);return "Job / patient: "+(group==null?"All tasks":group.isEmpty()?"Ungrouped":group); }
    static List<TaskStore.Task> filter(Context context,List<TaskStore.Task> tasks) {
        List<TaskStore.Task> result=new ArrayList<>();for(TaskStore.Task task:tasks) if(matches(context,task.group)) result.add(task);return result;
    }
    static List<String> names(Context context) {
        Set<String> names=new TreeSet<>(context.getSharedPreferences("settings",0).getStringSet("groups",Collections.emptySet()));
        try(TaskStore store=new TaskStore(context); Cursor c=store.getReadableDatabase().rawQuery("SELECT task_group FROM tasks UNION SELECT task_group FROM shift_runs UNION SELECT task_group FROM bundles",null)) {
            while(c.moveToNext()) if(!c.getString(0).isEmpty()) names.add(c.getString(0));
        }
        return new ArrayList<>(names);
    }
    static void choose(Activity activity,Runnable refresh) {
        List<String> groups=names(activity);List<String> labels=new ArrayList<>();
        labels.add("All tasks");labels.add("Ungrouped");labels.addAll(groups);labels.add("+ New job / patient / group");
        new AlertDialog.Builder(activity).setTitle("Choose job, patient or group").setItems(labels.toArray(new String[0]),(d,index)->{
            if(index==labels.size()-1) { create(activity,group->{select(activity,group);refresh.run();});return; }
            select(activity,index==0?null:index==1?"":groups.get(index-2));refresh.run();
        }).setNegativeButton("Cancel",null).show();
    }
    static void select(Context context,String group) {
        android.content.SharedPreferences.Editor edit=context.getSharedPreferences("settings",0).edit();
        if(group==null)edit.remove("group");else edit.putString("group",group);edit.apply();TaskWidget.refreshAll(context);
    }
    static void move(Activity activity,TaskStore.Task task,Runnable refresh) {
        List<String> groups=names(activity);List<String> labels=new ArrayList<>();labels.add("Ungrouped");labels.addAll(groups);labels.add("+ New group");
        Consumer<String> save=group->{try(TaskStore store=new TaskStore(activity)) {
            if(task.shiftRunId>0) {
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try {
                    android.content.ContentValues values=new android.content.ContentValues();values.put("task_group",group);
                    db.update("shift_runs",values,"_id=?",new String[]{""+task.shiftRunId});
                    db.update("tasks",values,"shift_run_id=?",new String[]{""+task.shiftRunId});db.setTransactionSuccessful();
                } finally {db.endTransaction();}
            } else store.setGroup(task.id,group);
        }TaskWidget.refreshAll(activity);refresh.run();};
        new AlertDialog.Builder(activity).setTitle(task.shiftRunId>0?"Move whole shift to group":"Move task to group").setItems(labels.toArray(new String[0]),(d,index)->{
            if(index==labels.size()-1)create(activity,save);else save.accept(index==0?"":groups.get(index-1));
        }).setNegativeButton("Cancel",null).show();
    }
    private static void create(Activity activity,Consumer<String> done) {
        EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("Job, patient or other group");
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("New group").setView(name).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String group=clean(name.getText().toString());if(group.isEmpty())throw new IllegalArgumentException("Enter a group name");
                Set<String> names=new HashSet<>(names(activity));names.add(group);
                activity.getSharedPreferences("settings",0).edit().putStringSet("groups",names).apply();done.accept(group);dialog.dismiss();
            } catch(IllegalArgumentException e) { name.setError(e.getMessage()); }
        }));dialog.show();
    }
}
