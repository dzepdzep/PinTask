package com.pintask.app;

import android.app.*;
import android.content.ContentValues;
import android.database.Cursor;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Optional reusable sets of untimed tasks; never required to schedule a shift. */
final class TaskBundles {
    static void validate(String value) {
        if(value==null || value.length()>250000)throw new IllegalArgumentException("Bundle is too large");
        try{JSONArray rows=new JSONArray(value);if(rows.length()<1 || rows.length()>50)throw new IllegalArgumentException("Use 1–50 tasks per bundle");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.length()!=4 || !(row.get("title") instanceof String) || row.getString("title").trim().isEmpty() || row.getString("title").length()>160 || !(row.get("notes") instanceof String) || row.getString("notes").length()>4000 || !(row.get("checklist") instanceof String) || !(row.get("priority") instanceof Integer) || row.getInt("priority")<0 || row.getInt("priority")>2)throw new IllegalArgumentException("Invalid bundle task");TaskDetails.validate(row.getString("checklist"));}}
        catch(JSONException e){throw new IllegalArgumentException("Invalid bundle");}
    }
    static long save(TaskStore store,String name,String group,String payload) {
        String cleaned=name.trim();if(cleaned.isEmpty() || cleaned.length()>80)throw new IllegalArgumentException("Use a bundle name up to 80 characters");validate(payload);
        ContentValues values=new ContentValues();values.put("name",cleaned);values.put("task_group",TaskGroups.clean(group));values.put("payload",payload);return store.getWritableDatabase().insertOrThrow("bundles",null,values);
    }
    static String capture(List<TaskStore.Task> tasks) {
        JSONArray rows=new JSONArray();try{for(TaskStore.Task task:tasks)rows.put(new JSONObject().put("title",task.title).put("priority",task.priority).put("notes",task.notes).put("checklist",TaskDetails.reset(task.checklist)));}catch(JSONException e){throw new IllegalArgumentException(e);}validate(rows.toString());return rows.toString();
    }
    static List<Long> apply(TaskStore store,long id,String selectedGroup) {
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try(Cursor c=db.query("bundles",null,"_id=?",new String[]{""+id},null,null,null)) {
            if(!c.moveToFirst())throw new IllegalArgumentException("Bundle no longer exists");String payload=c.getString(c.getColumnIndexOrThrow("payload"));validate(payload);String group=selectedGroup==null?c.getString(c.getColumnIndexOrThrow("task_group")):selectedGroup;
            JSONArray rows=new JSONArray(payload);List<Long> ids=new ArrayList<>();
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);TaskStore.Task task=store.add(row.getString("title"),row.getInt("priority"),0,0,-2);store.setGroup(task.id,group);store.saveDetails(task.id,row.getString("notes"),TaskDetails.reset(row.getString("checklist")),false);ids.add(task.id);}
            db.setTransactionSuccessful();return ids;
        }catch(JSONException e){throw new IllegalArgumentException(e);}finally{db.endTransaction();}
    }
    static void show(Activity activity,Runnable refresh) {
        List<Long> ids=new ArrayList<>();List<String> names=new ArrayList<>();
        try(TaskStore store=new TaskStore(activity);Cursor c=store.getReadableDatabase().query("bundles",null,null,null,null,null,"name COLLATE NOCASE")){while(c.moveToNext()){String group=c.getString(c.getColumnIndexOrThrow("task_group"));ids.add(c.getLong(0));names.add(c.getString(c.getColumnIndexOrThrow("name"))+(group.isEmpty()?"":" · "+group));}}
        names.add("+ New bundle");names.add("+ Save selected existing tasks as bundle");
        new AlertDialog.Builder(activity).setTitle("Bundles · add to "+(TaskGroups.selected(activity)==null?"saved group":TaskGroups.newTaskGroup(activity).isEmpty()?"Ungrouped":TaskGroups.newTaskGroup(activity))).setItems(names.toArray(new String[0]),(d,index)->{
            if(index==ids.size())create(activity,refresh);else if(index==ids.size()+1)captureUi(activity,refresh);else {
                try(TaskStore store=new TaskStore(activity)){int count=apply(store,ids.get(index),TaskGroups.selected(activity)).size();Toast.makeText(activity,"Added "+count+" personal tasks",Toast.LENGTH_SHORT).show();refresh.run();}
            }
        }).setNegativeButton("Close",null).setNeutralButton("Remove bundle",(d,w)->{
            if(ids.isEmpty())return;String[] labels=names.subList(0,ids.size()).toArray(new String[0]);new AlertDialog.Builder(activity).setTitle("Remove saved bundle").setItems(labels,(dialog,index)->new AlertDialog.Builder(activity).setTitle("Remove "+labels[index]+"?").setMessage("Existing tasks will stay saved.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(a,b)->{try(TaskStore store=new TaskStore(activity)){store.getWritableDatabase().delete("bundles","_id=?",new String[]{""+ids.get(index)});}}).show()).show();
        }).show();
    }
    static void create(Activity activity,Runnable refresh) {
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);EditText name=new EditText(activity);name.setHint("Bundle name");body.addView(name);EditText lines=new EditText(activity);lines.setHint("One task name per line");lines.setMinLines(4);body.addView(lines);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("New task bundle").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save bundle",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try(TaskStore store=new TaskStore(activity)){List<TaskStore.Task> tasks=new ArrayList<>();for(String line:lines.getText().toString().split("\\r?\\n")){if(line.trim().isEmpty())continue;tasks.add(new TaskStore.Task(0,line.trim(),0,false,0,1,0,0,0,-2,0,""));}save(store,name.getText().toString(),TaskGroups.newTaskGroup(activity),capture(tasks));dialog.dismiss();refresh.run();}catch(IllegalArgumentException e){lines.setError(e.getMessage());}}));dialog.show();
    }
    private static void captureUi(Activity activity,Runnable refresh) {
        List<TaskStore.Task> tasks;try(TaskStore store=new TaskStore(activity)){tasks=TaskGroups.filter(activity,store.active());}if(tasks.isEmpty()){Toast.makeText(activity,"Add some tasks first",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[tasks.size()];for(int i=0;i<tasks.size();i++)labels[i]=tasks.get(i).title;boolean[] selected=new boolean[tasks.size()];
        new AlertDialog.Builder(activity).setTitle("Choose tasks for bundle").setMultiChoiceItems(labels,selected,(d,i,value)->selected[i]=value).setNegativeButton("Cancel",null).setPositiveButton("Next",(d,w)->{
            List<TaskStore.Task> chosen=new ArrayList<>();for(int i=0;i<tasks.size();i++)if(selected[i])chosen.add(tasks.get(i));if(chosen.isEmpty() || chosen.size()>50){Toast.makeText(activity,"Choose 1–50 tasks",Toast.LENGTH_SHORT).show();return;}
            EditText name=new EditText(activity);name.setHint("Bundle name");AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Save task bundle").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
            dialog.setOnShowListener(a->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try(TaskStore store=new TaskStore(activity)){save(store,name.getText().toString(),TaskGroups.newTaskGroup(activity),capture(chosen));dialog.dismiss();refresh.run();}catch(IllegalArgumentException e){name.setError(e.getMessage());}}));dialog.show();
        }).show();
    }
}
