package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** No voice command changes data before this editable human confirmation. */
final class VoiceTaskReview {
    private VoiceTaskReview(){}
    static void show(Activity activity,VoiceTaskParser.Draft draft,Runnable saved){
        if(draft.midnightNeedsConfirmation){
            new AlertDialog.Builder(activity)
                    .setTitle("What did you mean by 24:00?")
                    .setMessage("24:00 can be misunderstood in speech recognition. Pick the exact midnight before the task is saved.")
                    .setItems(new String[]{
                            "End of named day · 00:00 the following day",
                            "Start of named day · 00:00 that day"
                    },(dialog,choice)->{
                        if(choice==0)draft.day=draft.day.plusDays(1);
                        draft.midnightNeedsConfirmation=false;
                        show(activity,draft,saved);
                    })
                    .setNegativeButton("Cancel",null).show();
            return;
        }
        int gap=dp(activity,12);
        LinearLayout contents=new LinearLayout(activity);
        contents.setOrientation(LinearLayout.VERTICAL);
        contents.setPadding(gap*2,gap,gap*2,gap);
        TextView transcript=new TextView(activity);
        transcript.setText("Heard: "+draft.transcript);
        transcript.setTextSize(13);transcript.setMaxLines(3);
        contents.addView(transcript);
        TextView nameLabel=new TextView(activity);nameLabel.setText("Task name");nameLabel.setTextSize(14);
        contents.addView(nameLabel);
        EditText title=new EditText(activity);
        title.setSingleLine(true);title.setText(draft.title);title.setHint("What needs doing?");
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        contents.addView(title);
        TextView schedule=new TextView(activity);schedule.setTextSize(15);
        schedule.setPadding(0,gap,0,gap);
        schedule.setTypeface(null,Typeface.BOLD);
        contents.addView(schedule);
        Button date=new Button(activity);date.setAllCaps(false);
        date.setContentDescription("Choose reminder date");
        contents.addView(date);
        Button clock=new Button(activity);clock.setAllCaps(false);
        clock.setContentDescription("Choose reminder time");
        contents.addView(clock);
        Button repeat=new Button(activity);repeat.setAllCaps(false);
        repeat.setContentDescription("Choose recurrence");
        contents.addView(repeat);
        TextView notice=new TextView(activity);
        notice.setTextSize(13);notice.setPadding(0,gap,0,0);
        contents.addView(notice);
        final DateTimeFormatter format=DateTimeFormatter.ofPattern("EEE, d MMM yyyy",Locale.getDefault());
        Runnable refresh=()->{
            boolean hourly=draft.recurrence>=5001&&draft.recurrence<=5024;
            String when=hourly?"First reminder "+(draft.recurrence-5000)+" hour(s) after saving":
                    draft.hasTime()?draft.day.format(format)+
                            String.format(Locale.ROOT," at %02d:%02d",draft.hour,draft.minute):
                            draft.day==null?"No time · show as a normal task":"Time needed";
            schedule.setText("Preview: "+when);
            date.setText("Date: "+(draft.day==null?"Choose":draft.day.format(format)));
            clock.setText("Time: "+(draft.hasTime()?
                    String.format(Locale.ROOT,"%02d:%02d",draft.hour,draft.minute):
                    "Choose time"));
            repeat.setText("Repeat: "+(draft.recurrence==0?"Never":RepeatRules.label(draft.recurrence)));
            date.setVisibility(hourly?android.view.View.GONE:android.view.View.VISIBLE);
            clock.setVisibility(hourly?android.view.View.GONE:android.view.View.VISIBLE);
            if(draft.recurrence>0 && !hourly&&!draft.hasTime())notice.setText("Choose a time before saving this repeating reminder.");
            else if(draft.day!=null&&!draft.hasTime())notice.setText("Choose an alert time or clear the date to add an untimed task.");
            else if(draft.hasTime()&&!hourly&&draft.dueMillis(ZoneId.systemDefault())<=System.currentTimeMillis())
                notice.setText("The time is in the past. Choose a future date or time.");
            else notice.setText("Nothing is saved until you confirm. Alarm delivery depends on Android reminder permissions.");
        };
        date.setOnClickListener(v->{
            LocalDate initial=draft.day==null?LocalDate.now():draft.day;
            new DatePickerDialog(activity,(picker,y,m,d)->{
                draft.day=LocalDate.of(y,m+1,d);
                refresh.run();
            },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
        });
        clock.setOnClickListener(v->{
            LocalTime initial=draft.hasTime()?LocalTime.of(draft.hour,draft.minute):LocalTime.now();
            new TimePickerDialog(activity,(picker,h,m)->{
                draft.hour=h;draft.minute=m;
                if(draft.day==null)draft.day=LocalDate.now();
                refresh.run();
            },initial.getHour(),initial.getMinute(),true).show();
        });
        repeat.setOnClickListener(v->new AlertDialog.Builder(activity)
                .setTitle("Repeat this task?")
                .setSingleChoiceItems(new String[]{"Never","Every day","Every week","Every other week"},
                        draft.recurrence==TaskStore.REPEAT_DAILY?1:
                        draft.recurrence==TaskStore.REPEAT_WEEKLY?2:
                        draft.recurrence==TaskStore.REPEAT_FORTNIGHTLY?3:0,
                        (dialog,chosen)->{
                            draft.recurrence=new int[]{0,1,2,4}[chosen];
                            dialog.dismiss();refresh.run();
                        }).setNegativeButton("Cancel",null).show());
        refresh.run();
        ScrollView scroll=new ScrollView(activity);scroll.addView(contents);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Confirm spoken task")
                .setView(scroll)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save task",null).create();
        dialog.setOnShowListener(which->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v->{
                    String value=title.getText().toString().trim();
                    if(value.isEmpty()){title.setError("Enter a task name");return;}
                    try{
                        boolean hourly=draft.recurrence>=5001&&draft.recurrence<=5024;
                        boolean timed=hourly || draft.day!=null || draft.hasTime();
                        long due=hourly?System.currentTimeMillis()+
                                (draft.recurrence-5000)*3600000L:
                                timed?draft.dueMillis(ZoneId.systemDefault()):0;
                        if(due>0 && due<=System.currentTimeMillis())
                            throw new IllegalArgumentException("That reminder time has passed. Choose a future time.");
                        if(draft.recurrence!=0 && due==0)
                            throw new IllegalArgumentException("Repeating reminders need a time.");
                        try(TaskStore store=new TaskStore(activity)){
                            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();
                            db.beginTransaction();
                            try{
                                TaskStore.Task created=store.add(value,TaskStore.PRIORITY_NORMAL,
                                        due,draft.recurrence,due==0?-1:0);
                                store.setGroup(created.id,"");
                                if(draft.recurrence>0)store.enableAutomaticRoutine(created.id);
                                db.setTransactionSuccessful();
                            }finally{db.endTransaction();}
                        }
                        TaskNotifications.syncAll(activity);
                        ReminderScheduler.rescheduleAll(activity);
                        TaskWidget.refreshAll(activity);
                        dialog.dismiss();
                        saved.run();
                        if(due>0)ReminderHealth.warnWhenBlocked(activity);
                        Toast.makeText(activity,"Spoken task saved",Toast.LENGTH_LONG).show();
                    }catch(IllegalArgumentException error){
                        notice.setText(error.getMessage());
                        Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
    }
    private static int dp(Context context,int v){
        return Math.round(v*context.getResources().getDisplayMetrics().density);
    }
}
