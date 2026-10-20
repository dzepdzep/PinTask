package com.pintask.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.speech.RecognizerIntent;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Android speech intent + explicit, editable confirmation. No always-on recording. */
final class VoiceTaskEntry {
    static final int SPEECH_REQUEST=73;
    private static final String[] REPEATS={
        "Once","Every day","Every week","Every other week",
        "Every hour","Every 2 hours","Every 3 hours","Every 4 hours"
    };
    private static final int[] CODES={0,1,2,4,5001,5002,5003,5004};
    private VoiceTaskEntry(){}

    static void start(ShiftActivity activity){
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Say a task and time, like ‘Wash dishes tomorrow at 13’");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        try {
            activity.startActivityForResult(intent,SPEECH_REQUEST);
        }catch(android.content.ActivityNotFoundException unavailable){
            new AlertDialog.Builder(activity)
                    .setTitle("Speech recognition unavailable")
                    .setMessage("No Android voice recognition service is installed. You can still type a natural-language command.")
                    .setPositiveButton("Type command",(d,w)->manual(activity))
                    .setNegativeButton("Cancel",null).show();
        }catch(SecurityException denied){
            Toast.makeText(activity,"Voice input is blocked. Check your phone's speech service permissions.",Toast.LENGTH_LONG).show();
        }
    }

    static void manual(ShiftActivity activity){
        EditText phrase=new EditText(activity);
        phrase.setSingleLine(false);
        phrase.setMinLines(2);
        phrase.setHint("Take out the bins every other Tuesday at 7");
        int pad=dp(activity,20);
        FrameLayout wrap=new FrameLayout(activity);wrap.setPadding(pad,0,pad,0);
        wrap.addView(phrase);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Type a task command")
                .setView(wrap)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Review",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String words=phrase.getText().toString().trim();
            if(words.isEmpty()){phrase.setError("Enter a command");return;}
            dialog.dismiss();review(activity,words);
        }));
        dialog.show();
    }

    static void review(ShiftActivity activity,String recognized){
        final VoiceTaskParser.Result parsed;
        try{parsed=VoiceTaskParser.parse(recognized,ZonedDateTime.now());}
        catch(IllegalArgumentException invalid){
            new AlertDialog.Builder(activity).setTitle("Couldn't understand the command")
                    .setMessage(invalid.getMessage()+"\n\nYou can edit the words and try again.")
                    .setPositiveButton("Edit words",(d,w)->manual(activity))
                    .setNegativeButton("Cancel",null).show();
            return;
        }

        int pad=dp(activity,18);
        LinearLayout fields=new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(pad,pad,pad,pad);
        TextView transcription=label(activity,"HEARD: "+recognized,13);
        transcription.setContentDescription("Recognized words: "+recognized);
        fields.addView(transcription);
        TextView titleCaption=label(activity,"TASK NAME",12);fields.addView(titleCaption);
        EditText title=new EditText(activity);title.setText(parsed.title);
        title.setSingleLine(true);title.setHint("What needs doing?");
        title.setSelectAllOnFocus(true);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        fields.addView(title);

        final LocalDate[] day={parsed.date};
        final LocalTime[] time={parsed.time};
        final boolean[] timed={parsed.scheduleMentioned};
        if(!parsed.note.isEmpty()){
            TextView caution=label(activity,parsed.note,13);
            fields.addView(caution);
        }
        Button date=button(activity,"Date: "+day[0],null);
        Button hour=button(activity,"Time: "+(time[0]==null?"Not set — choose a time":time[0].format(DateTimeFormatter.ofPattern("HH:mm"))),null);
        fields.addView(date);fields.addView(hour);
        TextView repeatTitle=label(activity,"REPEAT",12);fields.addView(repeatTitle);
        Spinner recurrence=new Spinner(activity);
        ArrayAdapter<String> repeats=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,REPEATS);
        repeats.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrence.setAdapter(repeats);
        int repeatIndex=0;
        for(int i=0;i<CODES.length;i++)if(CODES[i]==parsed.recurrence)repeatIndex=i;
        recurrence.setSelection(repeatIndex);
        fields.addView(recurrence);

        TextView summary=label(activity,"",14);
        fields.addView(summary);
        Runnable update=()->{
            int repeatCode=CODES[recurrence.getSelectedItemPosition()];
            boolean needsTime=timed[0]||repeatCode!=0;
            date.setVisibility(needsTime?View.VISIBLE:View.GONE);
            hour.setVisibility(needsTime?View.VISIBLE:View.GONE);
            summary.setText(!needsTime?"No time specified · Pin this task immediately (no alarm).":
                    time[0]==null?"Choose a clock time before saving an alarm.":
                    "Alert "+day[0]+" at "+time[0].format(DateTimeFormatter.ofPattern("HH:mm"))+
                    " · "+REPEATS[recurrence.getSelectedItemPosition()]);
        };
        recurrence.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos!=0)timed[0]=true;
                update.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        date.setOnClickListener(v->{
            LocalDate selected=day[0];
            DatePickerDialog picker=new DatePickerDialog(activity,(view,y,m,d)->{
                day[0]=LocalDate.of(y,m+1,d);date.setText("Date: "+day[0]);update.run();
            },selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth());
            picker.show();
        });
        hour.setOnClickListener(v->{
            LocalTime current=time[0]==null?LocalTime.now().plusHours(1):time[0];
            new TimePickerDialog(activity,(view,h,m)->{
                time[0]=LocalTime.of(h,m);
                hour.setText("Time: "+time[0].format(DateTimeFormatter.ofPattern("HH:mm")));
                timed[0]=true;update.run();
            },current.getHour(),current.getMinute(),true).show();
        });
        update.run();

        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);
        scroll.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Review your voice task")
                .setView(scroll)
                .setNegativeButton("Cancel",null)
                .setNeutralButton("Edit spoken words",(d,w)->manual(activity))
                .setPositiveButton("Save task",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String text=title.getText().toString().trim();
            if(text.isEmpty()){title.setError("Enter a task name");return;}
            int repeat=CODES[recurrence.getSelectedItemPosition()];
            boolean scheduled=timed[0]||repeat!=0;
            if(scheduled && time[0]==null){
                Toast.makeText(activity,"Please select a time first",Toast.LENGTH_LONG).show();
                return;
            }
            long due=scheduled?day[0].atTime(time[0]).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli():0;
            if(scheduled&&due<=System.currentTimeMillis()){
                Toast.makeText(activity,"This time is in the past. Choose a future date or time.",Toast.LENGTH_LONG).show();
                return;
            }
            try{
                TaskStore.Task saved;
                try(TaskStore store=new TaskStore(activity)){
                    saved=store.add(text,TaskStore.PRIORITY_NORMAL,due,repeat,scheduled?0:-1);
                    saved=store.setGroup(saved.id,"");
                    if(repeat!=0)saved=store.enableAutomaticRoutine(saved.id);
                }
                if(scheduled)ReminderScheduler.schedule(activity,saved);
                else TaskNotifications.show(activity,saved);
                TaskWidget.refreshAll(activity);
                dialog.dismiss();
                activity.voiceTaskSaved(scheduled);
            }catch(RuntimeException error){
                Toast.makeText(activity,"Couldn't save task: "+error.getMessage(),Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
    private static int dp(Activity activity,int value){
        return Math.round(value*activity.getResources().getDisplayMetrics().density);
    }
    private static TextView label(Activity activity,String words,int size){
        TextView t=new TextView(activity);t.setText(words);t.setTextSize(size);
        t.setPadding(0,dp(activity,6),0,dp(activity,8));return t;
    }
    private static Button button(Activity activity,String text,View.OnClickListener listener){
        Button b=new Button(activity);b.setText(text);b.setAllCaps(false);
        if(listener!=null)b.setOnClickListener(listener);return b;
    }
}
