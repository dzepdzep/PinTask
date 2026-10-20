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
                if(invalid.getMessage()!=null&&(invalid.getMessage().startsWith("Please specify a time")||
                        invalid.getMessage().startsWith("Please add a time"))){
                    new android.app.TimePickerDialog(activity,(picker,hour,minute)->{
                        String corrected=command.getText().toString().trim()+
                                String.format(java.util.Locale.ROOT," at %02d:%02d",hour,minute);
                        command.setText(corrected);
                        try{
                            SpokenTask amended=SpokenTask.parse(corrected,ZonedDateTime.now());
                            dialog.dismiss();
                            review(activity,amended,saved);
                        }catch(IllegalArgumentException wrong){
                            command.setError(wrong.getMessage());
                        }
                    },9,0,true).show();
                }else{
                    command.setError(invalid.getMessage());
                    Toast.makeText(activity,invalid.getMessage(),Toast.LENGTH_LONG).show();
                }
            }
        }));
        dialog.show();
    }

    private static final int FG=Color.rgb(238,244,251);
    private static final int MUTED=Color.rgb(156,173,194);

    private static int dp(Activity activity,int amount){
        return Math.round(amount*activity.getResources().getDisplayMetrics().density);
    }

    private static TextView label(Activity activity,String text,int size,boolean emphasized){
        TextView result=new TextView(activity);
        result.setText(text);
        result.setTextSize(size);
        result.setTextColor(emphasized?FG:MUTED);
        result.setPadding(0,dp(activity,8),0,dp(activity,3));
        return result;
    }

    /**
     * Everything Android understood is visible and independently correctable:
     * task text, due date, 24-hour clock time, and recurrence. No re-dictation.
     */
    private static void review(Activity activity,SpokenTask parsed,Runnable saved){
        VoiceReviewModel model=new VoiceReviewModel(parsed,java.time.ZoneId.systemDefault());
        int pad=dp(activity,18);

        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad,dp(activity,6),pad,dp(activity,12));

        panel.addView(label(activity,"WHAT I HEARD",12,false));
        TextView heard=label(activity,parsed.original,15,true);
        heard.setPadding(dp(activity,12),dp(activity,10),dp(activity,12),dp(activity,10));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.rgb(32,45,65));
        background.setCornerRadius(dp(activity,10));
        heard.setBackground(background);
        panel.addView(heard);
        panel.addView(label(activity,"Check the task, date, time, advance reminder and repeat. Tap any field to correct it.",13,false));

        panel.addView(label(activity,"TASK NAME",12,false));
        EditText title=new EditText(activity);
        title.setText(parsed.title);
        title.setTextSize(17);
        title.setTextColor(FG);
        title.setSingleLine(true);
        title.setSelectAllOnFocus(true);
        title.setContentDescription("Recognized task name");
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        panel.addView(title);

        panel.addView(label(activity,"DATE",12,false));
        android.widget.Button dateButton=new android.widget.Button(activity);
        dateButton.setAllCaps(false);
        dateButton.setContentDescription("Change reminder date");
        panel.addView(dateButton,new LinearLayout.LayoutParams(-1,dp(activity,49)));

        panel.addView(label(activity,"TIME · 24-HOUR CLOCK",12,false));
        android.widget.Button timeButton=new android.widget.Button(activity);
        timeButton.setAllCaps(false);
        timeButton.setContentDescription("Change reminder time");
        panel.addView(timeButton,new LinearLayout.LayoutParams(-1,dp(activity,49)));

        panel.addView(label(activity,"REMIND ME",12,false));
        android.widget.Button earlyButton=new android.widget.Button(activity);
        earlyButton.setAllCaps(false);
        earlyButton.setContentDescription("Change advance reminder");
        panel.addView(earlyButton,new LinearLayout.LayoutParams(-1,dp(activity,49)));

        panel.addView(label(activity,"REPEAT",12,false));
        android.widget.Button repeatButton=new android.widget.Button(activity);
        repeatButton.setAllCaps(false);
        repeatButton.setContentDescription("Change repeat pattern");
        panel.addView(repeatButton,new LinearLayout.LayoutParams(-1,dp(activity,49)));

        TextView summary=label(activity,"",14,true);
        summary.setPadding(0,dp(activity,10),0,dp(activity,8));
        panel.addView(summary);

        Runnable refresh=()->{
            dateButton.setText(model.dateLabel()+"   ✎");
            timeButton.setText(model.timeLabel()+"   ✎");
            repeatButton.setText(model.repeatLabel()+"   ▾");
            earlyButton.setText(model.advanceLabel()+"   ▾");
            summary.setText(model.preview()+
                    "\n"+(model.repeatCode()==0?"One-time task":"Repeats: "+model.repeatLabel())+
                    "\nNothing is saved until you tap Create task.");
        };
        refresh.run();
        dateButton.setOnClickListener(v->{
            java.time.LocalDate initial=model.day()!=null?model.day():java.time.LocalDate.now();
            new android.app.DatePickerDialog(activity,(picker,year,month,day)->{
                model.changeDate(java.time.LocalDate.of(year,month+1,day));
                refresh.run();
            },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
        });
        timeButton.setOnClickListener(v->{
            java.time.LocalTime initial=model.time()!=null?model.time():java.time.LocalTime.now();
            new android.app.TimePickerDialog(activity,(picker,hour,minute)->{
                model.changeTime(java.time.LocalTime.of(hour,minute),java.time.LocalDate.now());
                refresh.run();
            },initial.getHour(),initial.getMinute(),true).show();
        });
        earlyButton.setOnClickListener(v->{
            String[] options={"At task time","5 minutes before","10 minutes before",
                    "15 minutes before","30 minutes before","1 hour before",
                    "Custom minutes before…"};
            int[] minutes={0,5,10,15,30,60};
            new AlertDialog.Builder(activity).setTitle("Remind me before")
                    .setItems(options,(d,which)->{
                        if(which<minutes.length){
                            model.changeAdvance(minutes[which]);
                            refresh.run();
                        }else{
                            EditText number=new EditText(activity);
                            number.setSingleLine(true);
                            number.setInputType(InputType.TYPE_CLASS_NUMBER);
                            number.setHint("Minutes before (1–1440)");
                            number.setText(""+(model.notifyBeforeMinutes()>0?
                                    model.notifyBeforeMinutes():20));
                            int padding=dp(activity,20);
                            LinearLayout wrap=new LinearLayout(activity);
                            wrap.setOrientation(LinearLayout.VERTICAL);
                            wrap.setPadding(padding,0,padding,0);
                            wrap.addView(number);
                            AlertDialog custom=new AlertDialog.Builder(activity)
                                    .setTitle("Custom advance reminder")
                                    .setMessage("How many minutes before the task should the alarm sound?")
                                    .setView(wrap).setNegativeButton("Cancel",null)
                                    .setPositiveButton("Set reminder",null).create();
                            custom.setOnShowListener(shown->
                                    custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
                                try{
                                    int amount=Integer.parseInt(number.getText().toString().trim());
                                    model.changeAdvance(amount);
                                    custom.dismiss();
                                    refresh.run();
                                }catch(IllegalArgumentException wrong){
                                    number.setError("Enter 1–1440 minutes.");
                                }
                            }));
                            custom.show();
                        }
                    }).setNegativeButton("Cancel",null).show();
        });
        repeatButton.setOnClickListener(v->{
            String[] options={
                    "Keep detected · "+model.repeatLabel(),
                    "Does not repeat",
                    "Every day",
                    "Every week",
                    "Every other week",
                    "Every other day",
                    "Every hour",
                    "Every 2 hours",
                    "Every 3 hours",
                    "Every 4 hours",
                    "Every "+(model.day()==null?"chosen weekday":model.day().getDayOfWeek().toString().substring(0,1)+
                            model.day().getDayOfWeek().toString().substring(1).toLowerCase(java.util.Locale.ROOT))
            };
            int currentWeekday=model.day()==null?java.time.LocalDate.now().getDayOfWeek().getValue():model.day().getDayOfWeek().getValue();
            int[] codes={model.repeatCode(),0,1,2,4,5,5001,5002,5003,5004,4000+(1<<(currentWeekday-1))};
            new AlertDialog.Builder(activity).setTitle("Repeat this task")
                    .setItems(options,(d,choice)->{
                        if(choice>0){model.changeRepeat(codes[choice]);refresh.run();}
                    }).setNegativeButton("Cancel",null).show();
        });

        android.widget.ScrollView scroll=new android.widget.ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Check your voice task")
                .setView(scroll)
                .setNegativeButton("Cancel",null)
                .setNeutralButton("Edit transcript",(d,w)->editCommand(activity,parsed.original,saved))
                .setPositiveButton("Create task",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String actual=title.getText().toString().trim();
            if(actual.isEmpty()){title.setError("Enter the task name");return;}
            try {
                long dueAt=model.validatedDueAt(System.currentTimeMillis());
                TaskStore.Task task;
                try(TaskStore store=new TaskStore(activity)){
                    task=store.add(actual,TaskStore.PRIORITY_NORMAL,dueAt,
                            model.repeatCode(),dueAt==0?-1:model.notifyBeforeMinutes());
                    task=store.setGroup(task.id,"");
                    if(model.repeatCode()!=0)task=store.enableAutomaticRoutine(task.id);
                }
                if(dueAt==0)TaskNotifications.show(activity,task);
                else ReminderScheduler.schedule(activity,task);
                TaskWidget.refreshAll(activity);
                dialog.dismiss();
                saved.run();
                if(dueAt>0)ReminderHealth.warnWhenBlocked(activity);
                Toast.makeText(activity,"Voice task saved",Toast.LENGTH_LONG).show();
            }catch(IllegalArgumentException error){
                Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();
                summary.setText(error.getMessage()+"\nTap Date, Time, Remind me, or Repeat to correct it.");
                summary.setTextColor(Color.rgb(255,180,163));
            }
        }));
        dialog.show();
    }
}
