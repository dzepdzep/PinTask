package com.pintask.app;

import android.app.*;
import android.widget.*;
import android.text.InputFilter;
import org.json.*;

/** Notes and independently persisted checklist steps; repeating tasks reset their ticks. */
final class TaskDetails {
    static JSONArray parse(String value) {
        try{return new JSONArray(value);}catch(JSONException e){throw new IllegalArgumentException("Invalid checklist");}
    }
    static void validate(String value) {
        if(value==null || value.length()>20000)throw new IllegalArgumentException("Checklist is too large");
        JSONArray rows=parse(value);if(rows.length()>50)throw new IllegalArgumentException("Use up to 50 checklist steps");
        try{for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);Object text=row.get("text");if(row.length()!=2 || !(text instanceof String) || ((String)text).trim().isEmpty() || ((String)text).length()>160 || !(row.get("checked") instanceof Boolean))throw new IllegalArgumentException("Invalid checklist step");}}
        catch(JSONException e){throw new IllegalArgumentException("Invalid checklist step");}
    }
    static String reset(String value) {
        JSONArray rows=parse(value);try{for(int i=0;i<rows.length();i++)rows.getJSONObject(i).put("checked",false);}catch(JSONException e){throw new IllegalArgumentException("Invalid checklist");}return rows.toString();
    }
    static String fromLines(String value,String previous) {
        JSONArray result=new JSONArray(),old=parse(previous);
        try{for(String line:value.split("\\r?\\n")){String text=line.trim();if(text.isEmpty())continue;int index=result.length();boolean checked=index<old.length() && old.getJSONObject(index).getString("text").equals(text) && old.getJSONObject(index).getBoolean("checked");result.put(new JSONObject().put("text",text).put("checked",checked));}}
        catch(JSONException e){throw new IllegalArgumentException("Invalid checklist");}
        validate(result.toString());return result.toString();
    }
    static String summary(TaskStore.Task task) {
        JSONArray rows=parse(task.checklist);int done=0;for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i).optBoolean("checked"))done++;
        return (task.paused?"Paused · ":"")+(task.repeatAfter && task.recurrence>0?"After completion · ":"")+(rows.length()>0?done+"/"+rows.length()+" steps · ":"")+(task.notes.isEmpty()?"":"Notes · ");
    }
    static void open(Activity activity,TaskStore.Task snapshot,Runnable refresh) {
        TaskStore.Task task;try(TaskStore store=new TaskStore(activity)){task=store.find(snapshot.id);}if(task==null)return;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
        TextView notes=new TextView(activity);notes.setText(task.notes.isEmpty()?"No notes yet":task.notes);body.addView(notes);
        JSONArray rows=parse(task.checklist);
        for(int i=0;i<rows.length();i++) {
            final int index=i;JSONObject row=rows.optJSONObject(i);CheckBox box=new CheckBox(activity);box.setText(row.optString("text"));box.setChecked(row.optBoolean("checked"));box.setEnabled(!task.done);body.addView(box);
            box.setOnCheckedChangeListener((v,checked)->{try(TaskStore store=new TaskStore(activity)){TaskStore.Task current=store.find(task.id);if(current==null)return;JSONArray items=parse(current.checklist);if(index>=items.length())return;items.getJSONObject(index).put("checked",checked);store.saveDetails(task.id,current.notes,items.toString(),current.repeatAfter);refresh.run();}catch(JSONException e){throw new IllegalArgumentException(e);}});
        }
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        new AlertDialog.Builder(activity).setTitle(task.title).setView(scroll).setPositiveButton("Close",null).setNeutralButton("Edit notes / steps",(d,w)->edit(activity,task,refresh)).show();
    }
    static void edit(Activity activity,TaskStore.Task task,Runnable refresh) {
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,0,pad,0);
        EditText notes=new EditText(activity);notes.setHint("Notes (optional)");notes.setText(task.notes);notes.setMinLines(2);notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});body.addView(notes);
        EditText steps=new EditText(activity);steps.setHint("Checklist: one step per line");StringBuilder lines=new StringBuilder();JSONArray rows=parse(task.checklist);for(int i=0;i<rows.length();i++){if(i>0)lines.append('\n');lines.append(rows.optJSONObject(i).optString("text"));}steps.setText(lines);steps.setMinLines(3);body.addView(steps);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Notes and checklist").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try(TaskStore store=new TaskStore(activity)){TaskStore.Task latest=store.find(task.id);if(latest==null)return;store.saveDetails(task.id,notes.getText().toString(),fromLines(steps.getText().toString(),latest.checklist),latest.repeatAfter);refresh.run();dialog.dismiss();}catch(IllegalArgumentException e){steps.setError(e.getMessage());}}));dialog.show();
    }
}
