package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.time.ZonedDateTime;

/** Review-before-save UI for spoken tasks, including offline typed fallback. */
final class VoiceTaskDialog {
    private VoiceTaskDialog(){}

    static void editCommand(Activity activity,String initial,Runnable saved) {
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(20*activity.getResources().getDisplayMetrics().density);
        panel.setPadding(padding,0,padding,0);
        TextView hint=new TextView(activity);
        hint.setText("Examples:\nWash the dishes today at 13:00\nTake out bins every other Tuesday at 08:00\nTake medication in 30 minutes\n\nRecognition may use your phone's speech service. Review before saving.");
        hint.setTextSize(14);
        hint.setPadding(0,0,0,padding/2);
        panel.addView(hint);
        EditText command=new EditText(activity);
        command.setHint("Speak or enter your task command");
        command.setText(initial);
        command.setSingleLine(false);
        command.setMinLines(2);
        command.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        command.setFilters(new InputFilter[]{new InputFilter.LengthFilter(700)});
        panel.addView(command);

        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Voice task · Check transcript")
                .setView(panel)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Review schedule",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                String spoken=command.getText().toString();
                SpokenTask parsed=SpokenTask.parse(spoken,ZonedDateTime.now());
                dialog.dismiss();
                review(activity,parsed,saved);
            }catch(IllegalArgumentException invalid){
                command.setError(invalid.getMessage());
                Toast.makeText(activity,invalid.getMessage(),Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private static void review(Activity activity,SpokenTask parsed,Runnable saved){
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(20*activity.getResources().getDisplayMetrics().density);
        panel.setPadding(padding,0,padding,0);

        TextView nameLabel=new TextView(activity);
        nameLabel.setText("Task name");panel.addView(nameLabel);
        EditText title=new EditText(activity);
        title.setText(parsed.title);
        title.setSingleLine(true);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        panel.addView(title);

        TextView dateLabel=new TextView(activity);
        dateLabel.setText("Scheduled notification");
        dateLabel.setTextSize(15);
        dateLabel.setPadding(0,padding/2,0,0);
        panel.addView(dateLabel);
        TextView dateText=new TextView(activity);
        dateText.setText(parsed.schedule);
        dateText.setTextSize(17);
        dateText.setPadding(0,0,0,padding/2);
        panel.addView(dateText);

        TextView warning=new TextView(activity);
        warning.setText("Confirm the date, time and repeat schedule before creating this task. If they're incorrect, choose Edit command.");
        warning.setTextSize(13);
        panel.addView(warning);

        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Confirm voice task")
                .setView(panel)
                .setNegativeButton("Cancel",null)
                .setNeutralButton("Edit command",(d,w)->editCommand(activity,parsed.original,saved))
                .setPositiveButton("Create task",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String actual=title.getText().toString().trim();
            if(actual.isEmpty()){title.setError("Enter the task name");return;}
            try {
                if(parsed.dueAt>0&&parsed.dueAt<=System.currentTimeMillis())
                    throw new IllegalArgumentException("The reminder time has passed. Edit the command.");
                TaskStore.Task task;
                try(TaskStore store=new TaskStore(activity)){
                    // Timed tasks are always alerts at task time, never silently No reminder.
                    task=store.add(actual,TaskStore.PRIORITY_NORMAL,parsed.dueAt,
                            parsed.repeatCode,parsed.dueAt==0?-1:0);
                    task=store.setGroup(task.id,"");
                    if(parsed.repeatCode!=0)task=store.enableAutomaticRoutine(task.id);
                }
                if(parsed.dueAt==0)TaskNotifications.show(activity,task);
                else ReminderScheduler.schedule(activity,task);
                TaskWidget.refreshAll(activity);
                dialog.dismiss();
                saved.run();
                if(parsed.dueAt>0)ReminderHealth.warnWhenBlocked(activity);
                Toast.makeText(activity,"Voice task saved",Toast.LENGTH_LONG).show();
            } catch(IllegalArgumentException error) {
                Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
}
