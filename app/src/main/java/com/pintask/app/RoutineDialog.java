package com.pintask.app;

import android.app.*;
import android.content.*;
import android.text.InputFilter;
import android.widget.*;
import android.view.*;
import java.time.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Regular personal task setup on the primary Tasks screen, no shift required. */
final class RoutineDialog {
    private static final String[] TYPES={
        "Every day · one time","Every day · several times",
        "Every hour","Every 2 hours","Every 3 hours","Every 4 hours","Every N hours…"
    };
    private RoutineDialog(){}

    static void show(Activity activity,String suggestedTitle,Runnable completed) {
        final ArrayList<Integer> dailyTimes=new ArrayList<>();
        dailyTimes.add(9*60);
        final int[] hours={1};
        int gap=dp(activity,12);
        LinearLayout content=new LinearLayout(activity);
        content.setPadding(gap*2,gap,gap*2,gap);
        content.setOrientation(LinearLayout.VERTICAL);
        EditText title=new EditText(activity);
        title.setSingleLine(true);title.setHint("What should repeat?");
        title.setText(suggestedTitle);title.setSelectAllOnFocus(true);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        content.addView(title);
        TextView caption=new TextView(activity);caption.setText("Repeat schedule");caption.setTextSize(14);
        content.addView(caption);
        Spinner type=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item,TYPES);
        type.setAdapter(adapter);content.addView(type);
        TextView explanation=new TextView(activity);
        content.addView(explanation);
        LinearLayout times=new LinearLayout(activity);times.setOrientation(LinearLayout.VERTICAL);
        content.addView(times);
        Button addTime=new Button(activity);addTime.setAllCaps(false);addTime.setText("＋ Add daily time");
        content.addView(addTime);
        TextView summary=new TextView(activity);
        summary.setPadding(0,dp(activity,6),0,dp(activity,8));
        summary.setTextSize(14);content.addView(summary);
        Runnable refresh=()->{
            int selected=type.getSelectedItemPosition();
            boolean daily=selected<=1;
            times.removeAllViews();
            times.setVisibility(daily?View.VISIBLE:View.GONE);
            addTime.setVisibility(selected==1?View.VISIBLE:View.GONE);
            if(daily)for(Integer minute:new ArrayList<>(dailyTimes)){
                LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
                Button clock=new Button(activity);clock.setAllCaps(false);
                clock.setText(String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60));
                clock.setContentDescription("Change daily time");
                row.addView(clock,new LinearLayout.LayoutParams(0,dp(activity,48),1));
                clock.setOnClickListener(v->time(activity,minute,chosen->{
                    int pos=dailyTimes.indexOf(minute);
                    if(pos>=0){dailyTimes.set(pos,chosen);times.removeAllViews();} 
                    // Refresh the list once the time picker is closed.
                    type.setSelection(selected);
                    refreshTimes(activity,times,dailyTimes,selected,()->type.setSelection(selected));
                }));
                if(selected==1){
                    Button remove=new Button(activity);
                    remove.setText("×");remove.setContentDescription("Remove daily time");
                    row.addView(remove,new LinearLayout.LayoutParams(dp(activity,50),dp(activity,48)));
                    remove.setOnClickListener(v->{
                        dailyTimes.remove(minute);
                        refreshTimes(activity,times,dailyTimes,selected,()->type.setSelection(selected));
                    });
                }
                times.addView(row);
            }
            if(daily){
                explanation.setText(selected==0?"Alert at the same time every day.":
                        "Make an independent daily reminder for each time you choose.");
                summary.setText(dailyTimes.isEmpty()?"Choose at least one time.":
                        dailyTimes.size()+" daily time"+(dailyTimes.size()==1?"":"s")+" selected");
            }else{
                hours[0]=selected==2?1:selected==3?2:selected==4?3:selected==5?4:hours[0];
                explanation.setText("First alert in "+hours[0]+" hour"+(hours[0]==1?"":"s")+
                        ". Afterwards every "+hours[0]+" hour"+(hours[0]==1?"":"s")+".");
                summary.setText("New occurrences are scheduled even when an earlier task is still unfinished.");
            }
        };
        // Clock row creation is centralized after spinner changes.
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position==6){
                    NumberPicker picker=new NumberPicker(activity);
                    picker.setMinValue(1);picker.setMaxValue(24);picker.setValue(hours[0]);
                    new AlertDialog.Builder(activity).setTitle("Repeat every N hours")
                            .setView(picker).setNegativeButton("Cancel",(d,w)->type.setSelection(2))
                            .setPositiveButton("Use interval",(d,w)->{
                                hours[0]=picker.getValue();refresh.run();
                            }).show();
                }
                refresh.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        addTime.setOnClickListener(v->time(activity,LocalTime.now().getHour()*60,chosen->{
            if(!dailyTimes.contains(chosen))dailyTimes.add(chosen);
            Collections.sort(dailyTimes);
            refreshTimes(activity,times,dailyTimes,1,()->{});
            summary.setText(dailyTimes.size()+" daily times selected");
        }));
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Regular task")
                .setMessage("Choose how often PinTask should remind you. Each occurrence has its own Done and Snooze.")
                .setView(scroll).setNegativeButton("Cancel",null)
                .setPositiveButton("Save regular task",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String taskName=title.getText().toString().trim();
            if(taskName.isEmpty()){title.setError("Enter the task name");return;}
            int option=type.getSelectedItemPosition();
            if(option<=1 && dailyTimes.isEmpty()){
                Toast.makeText(activity,"Add at least one daily time",Toast.LENGTH_LONG).show();return;
            }
            try {
                int repeatCode=option<=1?TaskStore.REPEAT_DAILY:5000+hours[0];
                List<Long> scheduled=firstOccurrences(option<=1,dailyTimes,hours[0],System.currentTimeMillis(),
                        ZoneId.systemDefault());
                try(TaskStore store=new TaskStore(activity)){
                    android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();
                    db.beginTransaction();
                    try{
                        for(long due:scheduled){
                            TaskStore.Task task=store.add(taskName,TaskStore.PRIORITY_NORMAL,due,repeatCode,0);
                            store.setGroup(task.id,"");
                            store.enableAutomaticRoutine(task.id);
                        }
                        db.setTransactionSuccessful();
                    }finally{db.endTransaction();}
                }
                ReminderScheduler.rescheduleAll(activity);
                TaskWidget.refreshAll(activity);
                dialog.dismiss();completed.run();
                ReminderHealth.warnWhenBlocked(activity);
                Toast.makeText(activity,scheduled.size()+" regular task schedule"+(scheduled.size()==1?"":"s")+" saved",Toast.LENGTH_LONG).show();
            }catch(IllegalArgumentException error){Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

    static List<Long> firstOccurrences(boolean daily,List<Integer> times,int everyHours,long now,ZoneId zone) {
        ArrayList<Long> due=new ArrayList<>();
        if(daily){
            for(int minute:new java.util.TreeSet<>(times)){
                if(minute<0||minute>1439)throw new IllegalArgumentException("Choose a valid daily time");
                LocalDate date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
                long at=date.atTime(minute/60,minute%60).atZone(zone).toInstant().toEpochMilli();
                if(at<=now)at=date.plusDays(1).atTime(minute/60,minute%60).atZone(zone).toInstant().toEpochMilli();
                due.add(at);
            }
        }else{
            if(everyHours<1||everyHours>24)throw new IllegalArgumentException("Choose 1 to 24 hours");
            due.add(now+everyHours*3600000L);
        }
        if(due.isEmpty())throw new IllegalArgumentException("Select a time");
        return due;
    }

    private static void time(Activity a,int initial,IntConsumer chosen){
        FlexibleTimePicker.clock(a,initial,chosen);
    }

    private static void refreshTimes(Activity activity,LinearLayout times,List<Integer> minutes,
            int selected,Runnable ignored) {
        times.removeAllViews();
        for(Integer minute:new ArrayList<>(minutes)){
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            Button clock=new Button(activity);clock.setAllCaps(false);
            clock.setText(String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60));
            clock.setContentDescription("Change daily time");
            row.addView(clock,new LinearLayout.LayoutParams(0,dp(activity,48),1));
            clock.setOnClickListener(v->time(activity,minute,chosen->{
                int position=minutes.indexOf(minute);
                if(position>=0)minutes.set(position,chosen);
                Collections.sort(minutes);
                refreshTimes(activity,times,minutes,selected,ignored);
            }));
            if(selected==1){
                Button remove=new Button(activity);remove.setText("×");
                remove.setContentDescription("Remove daily time");
                row.addView(remove,new LinearLayout.LayoutParams(dp(activity,50),dp(activity,48)));
                remove.setOnClickListener(v->{
                    minutes.remove(minute);
                    refreshTimes(activity,times,minutes,selected,ignored);
                });
            }
            times.addView(row);
        }
    }
    private static int dp(Activity activity,int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
