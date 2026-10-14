package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class BackupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(24,60,24,24); body.setFitsSystemWindows(true);
        TextView text=new TextView(this); text.setText("Backup & restore\n\nSave tasks, Done history, favorites and shift templates to a file you control. The backup is readable text; keep it somewhere private. Restoring replaces the data currently in PinTask."); body.addView(text);
        Button export=new Button(this); export.setText("Export backup"); body.addView(export);
        export.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE,"PinTask-backup.json"),1));
        Button restore=new Button(this); restore.setText("Restore from backup"); body.addView(restore);
        restore.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),2));
        Button back=new Button(this); back.setText("Back"); back.setOnClickListener(v->finish()); body.addView(back);
        setContentView(body);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        new Thread(()->{
            try {
                if(request==1) {
                    String json; try(TaskStore store=new TaskStore(this)) { json=BackupData.export(store); }
                    try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")) {
                        if(output==null) throw new IllegalArgumentException("Cannot open destination");
                        output.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    message("Backup saved");
                } else if(request==2) {
                    String json;
                    try(InputStream input=getContentResolver().openInputStream(uri); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                        if(input==null) throw new IllegalArgumentException("Cannot open file");
                        byte[] bytes=new byte[8192]; int n;
                        while((n=input.read(bytes))!=-1) { if(output.size()+n>10_000_000) throw new IllegalArgumentException("Backup is too large"); output.write(bytes,0,n); }
                        json=output.toString("UTF-8");
                    }
                    org.json.JSONObject root=BackupData.parse(json);
                    runOnUiThread(()->{ if(isFinishing() || isDestroyed()) return;
                        new AlertDialog.Builder(this).setTitle("Replace current data?")
                            .setMessage("This backup contains "+root.optJSONArray("tasks").length()+" tasks and "+root.optJSONArray("shift_templates").length()+" shift templates. Export your current data first if you want to keep it.")
                            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->restore(json)).show(); });
                }
            } catch(Exception e) { message("Could not complete backup operation: "+e.getMessage()); }
        },"PinTask-backup").start();
    }
    private void restore(String json) {
        new Thread(()->{
            try(TaskStore store=new TaskStore(this)) {
                List<TaskStore.Task> old=store.active();
                BackupData.restore(store,json);
                for(TaskStore.Task task:old) { ReminderScheduler.cancel(this,task.id); TaskNotifications.cancel(this,task.id); }
                TaskNotifications.syncAll(this); ReminderScheduler.rescheduleAll(this); TaskWidget.refreshAll(this);
                message("Backup restored");
            } catch(Exception e) { message("Restore failed; your previous data was kept. "+e.getMessage()); }
        },"PinTask-restore").start();
    }
    private void message(String message) { runOnUiThread(()->{ if(!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show(); }); }
}
