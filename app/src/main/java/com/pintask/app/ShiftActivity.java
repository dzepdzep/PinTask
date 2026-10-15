package com.pintask.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Simple calendar workflow backed by the original task and shift tables. */
public final class ShiftActivity extends Activity {
    private ShiftRepository repository;
    private LinearLayout body;
    private UndoBar undoBar;
    private int screen=0;
    private LocalDate date=LocalDate.now();
    private long taskRun=0;
    private String draft="";
    private int minute=-1, reminder=0, repeat=0;
    private EditText name;
    private boolean moreOptions;
    private Spinner reminders, repeats;
    private static final int[] EARLY={-2,0,5,10,15,30,60};
    private static final int[] INTERVALS={0,30,60,120,180,240};
    private static final int BG=Color.rgb(13,18,26), FG=Color.rgb(240,244,249);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository=new ShiftRepository(this);
        if(state!=null) {
            screen=state.getInt("screen"); date=LocalDate.parse(state.getString("date"));
            taskRun=state.getLong("run"); draft=state.getString("draft","");
            minute=state.getInt("minute",-1); reminder=state.getInt("reminder"); repeat=state.getInt("repeat");moreOptions=state.getBoolean("moreOptions");
        }
        render();
    }
    @Override protected void onResume() {
        super.onResume();
        TaskNotifications.syncAll(this); ReminderScheduler.rescheduleAll(this);
        if(repository!=null && screen!=2) render();
    }
    @Override protected void onDestroy() { repository.close(); super.onDestroy(); }
    private void remember() {
        if(name!=null) { draft=name.getText().toString(); reminder=reminders.getSelectedItemPosition(); repeat=taskRun==0?RepeatRules.selected(repeats):repeats.getSelectedItemPosition(); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        remember(); state.putInt("screen",screen); state.putString("date",date.toString());
        state.putLong("run",taskRun); state.putString("draft",draft); state.putInt("minute",minute);
        state.putInt("reminder",reminder); state.putInt("repeat",repeat);state.putBoolean("moreOptions",moreOptions); super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() { if(screen!=0) { remember(); screen=0; render(); } else super.onBackPressed(); }
    private void render() {
        name=null;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(20),dp(16),dp(16));
        if(Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(dp(16)+bars.left,dp(20)+bars.top,dp(16)+bars.right,dp(16)+bars.bottom);return insets;
        }); else root.setFitsSystemWindows(true);
        setContentView(root);
        LinearLayout nav=new LinearLayout(this); root.addView(nav);
        String[] tabs={"Today","Schedule","Add Task"};
        for(int i=0;i<tabs.length;i++) {
            final int page=i; Button tab=new Button(this); tab.setText(tabs[i]); tab.setAllCaps(false);
            tab.setTextColor(screen==i?Color.rgb(175,241,138):FG); nav.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
            tab.setOnClickListener(v->{ if(page==screen)return; remember(); if(page==2 && draft.isEmpty()) { moreOptions=false; ShiftRepository.Run current=repository.currentRun(); taskRun=current==null?0:current.id; date=LocalDate.now(); repeat=0; } screen=page; render(); });
        }
        ScrollView scroll=new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0,dp(16),0,dp(24)); scroll.addView(body);
        undoBar=new UndoBar(this); root.addView(undoBar);
        header();
        if(screen==0) today(); else if(screen==1) schedule(); else addTask();
    }
    private void today() {
        text("Today · "+LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")),22);
        ShiftRepository.Run current=repository.currentRun();
        if(current!=null) { action("Current: "+summary(current),()->{date=dateOf(current.startsAt);screen=1;render();});showTasks(repository.runTasks(current.id)); }
        else if(repository.runsForDate(LocalDate.now()).isEmpty())action("Choose today’s shift",()->{date=LocalDate.now();screen=1;render();});
        for(ShiftRepository.Run run:repository.runsForDate(LocalDate.now())) {
            if(current==null || run.id!=current.id) { action(summary(run),()->{date=dateOf(run.startsAt);screen=1;render();});showTasks(repository.runTasks(run.id)); }
        }
        long tomorrow=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try(TaskStore store=new TaskStore(this)) {
            List<TaskStore.Task> personal=new ArrayList<>(), earlier=new ArrayList<>();
            for(TaskStore.Task task:TaskGroups.filter(this,store.active())) {
                if(task.shiftRunId==0 && (task.dueAt==0 || task.dueAt<tomorrow)) personal.add(task);
                if(task.shiftRunId>0) {
                    ShiftRepository.Run run=repository.findRun(task.shiftRunId);
                    if(run!=null && run.endsAt<=System.currentTimeMillis() && !dateOf(run.startsAt).equals(LocalDate.now())) earlier.add(task);
                }
            }
            showTasks(personal);
            if(!earlier.isEmpty()) { text("Unfinished from earlier shifts",18); showTasks(earlier); }
        }
        action("Add task",()->{ taskRun=current==null?0:current.id; date=LocalDate.now(); repeat=0; screen=2; render(); });
    }
    private void header() {
        LinearLayout row=new LinearLayout(this);
        TextView group=new TextView(this);String selected=TaskGroups.selected(this);group.setText(selected==null?"All groups":selected.isEmpty()?"Ungrouped":selected);group.setTextColor(FG);group.setTextSize(14);group.setPadding(dp(8),dp(12),0,dp(12));
        group.setOnClickListener(v->{remember();TaskGroups.choose(this,()->{taskRun=0;repeat=0;render();});});row.addView(group,new LinearLayout.LayoutParams(0,-2,1));
        Button more=new Button(this);more.setText("More");more.setAllCaps(false);row.addView(more);more.setOnClickListener(v->moreMenu(more));body.addView(row);
    }
    private void moreMenu(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("Change job / patient").setOnMenuItemClickListener(i->{remember();TaskGroups.choose(this,()->{taskRun=0;repeat=0;render();});return true;});
        menu.getMenu().add("All tasks and search").setOnMenuItemClickListener(i->{startActivity(new Intent(this,MainActivity.class).putExtra("search_all",true));return true;});
        menu.getMenu().add("Task bundles").setOnMenuItemClickListener(i->{TaskBundles.show(this,this::render);return true;});
        menu.getMenu().add("Completed tasks").setOnMenuItemClickListener(i->{remember();showHistory();return true;});
        menu.getMenu().add("Saved templates").setOnMenuItemClickListener(i->{startActivity(new Intent(this,LegacyShiftActivity.class));return true;});
        menu.getMenu().add("Quick Add notification: "+(TaskNotifications.quickAddEnabled(this)?"On":"Off")).setOnMenuItemClickListener(i->{
            new AlertDialog.Builder(this).setTitle("Quick Add notification").setSingleChoiceItems(new String[]{"Off","On"},TaskNotifications.quickAddEnabled(this)?1:0,(d,index)->{
                TaskNotifications.setQuickAddEnabled(this,index==1);d.dismiss();
                if(index==1 && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);
            }).setNegativeButton("Cancel",null).show();return true;
        });
        menu.getMenu().add("Backup").setOnMenuItemClickListener(i->{startActivity(new Intent(this,BackupActivity.class));return true;});menu.show();
    }
    private void showHistory() {
        body.removeAllViews();header();text("Completed tasks",22);
        try(TaskStore store=new TaskStore(this)){for(TaskStore.Task task:TaskGroups.filter(this,store.completedAll()))body.addView(TaskCards.done(this,task,this::showHistory));}
        action("Back to Today",()->{screen=0;render();});
    }
    private void schedule() {
        text("Schedule",22);
        CalendarView calendar=new CalendarView(this);calendar.setDate(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());body.addView(calendar);
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);body.addView(details);renderDate(details);
        calendar.setOnDateChangeListener((v,y,m,d)->{date=LocalDate.of(y,m+1,d);renderDate(details);});
    }
    private void renderDate(LinearLayout details) {
        LinearLayout previous=body;body=details;body.removeAllViews();
        text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),18);
        List<ShiftRepository.Run> runs=repository.runsForDate(date);
        if(runs.isEmpty())action("Choose shift",this::chooseShift);
        else {
            for(ShiftRepository.Run run:runs) {
                action(summary(run)+"  ·  Change",()->shiftMenu(run));
                if(working(run))action("Add task",()->{taskRun=run.id;repeat=0;moreOptions=false;screen=2;render();});
            }
        }
        body=previous;
    }
    private void chooseShift() {
        String[] codes={"TD","ND","U8","EZ","Off"};
        new AlertDialog.Builder(this).setTitle("Shift for "+date.format(DateTimeFormatter.ofPattern("d MMM")))
            .setItems(new String[]{"TD · 08:00–20:00","ND · 20:00–08:00 next day","U8 · 08:00–16:00","EZ","Off"},(d,index)->{
                try{repository.schedule(date,codes[index]);render();}catch(IllegalArgumentException e){toast(e.getMessage());}
            }).setNegativeButton("Cancel",null).show();
    }
    private boolean working(ShiftRepository.Run run) {return run.templateId!=0 || (!run.label.equals("Off")&&!run.label.equals("EZ"));}
    private void shiftMenu(ShiftRepository.Run run) {
        new AlertDialog.Builder(this).setTitle(summary(run)).setItems(new String[]{"Choose another shift","View tasks","End / clear shift"},(d,index)->{
            if(index==0){chooseShift();return;}
            if(index==1){body.removeAllViews();header();text(summary(run),20);showTasks(repository.runTasks(run.id));action("Back to Schedule",this::render);return;}
            new AlertDialog.Builder(this).setTitle("End "+run.label+"?").setMessage("Unfinished tasks and reminders will be canceled. Completed history stays saved.")
                .setNegativeButton("Keep",null).setPositiveButton("End shift",(a,b)->{for(long id:repository.endShift(run.id)){ReminderScheduler.cancel(this,id);TaskNotifications.cancel(this,id);}render();}).show();
        }).setNegativeButton("Close",null).show();
    }
    private void addTask() {
        text("Add Task",22);
        ShiftRepository.Run run=repository.findRun(taskRun);
        if(run==null) taskRun=0;
        text(run==null?"Personal task":summary(run),17);
        LinearLayout main=body;
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);
        body=advanced;
        action("Change day / shift",()->{
            remember();
            new DatePickerDialog(this,(v,y,m,d)->{
                date=LocalDate.of(y,m+1,d);
                List<ShiftRepository.Run> choices=new ArrayList<>();
                for(ShiftRepository.Run r:repository.runsForDate(date)) if((r.templateId!=0 || (!r.label.equals("Off")&&!r.label.equals("EZ")))) choices.add(r);
                String[] items=new String[choices.size()+1]; items[0]="Personal task";
                for(int i=0;i<choices.size();i++) items[i+1]=summary(choices.get(i));
                new AlertDialog.Builder(this).setTitle("Add task for "+date).setItems(items,(dialog,index)->{taskRun=index==0?0:choices.get(index-1).id;repeat=0;render();}).show();
            },date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();
        });
        body=main;
        name=new EditText(this); name.setSingleLine(true); name.setTextColor(FG); name.setHintTextColor(Color.LTGRAY);
        name.setHint("Task name"); name.setText(draft); name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)}); body.addView(name);
        action(minute<0?"Time · Optional":("Time · "+String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60)),()->{
            remember(); int initial=minute<0?LocalTime.now().getHour()*60:minute;
            new TimePickerDialog(this,(v,h,m)->{minute=h*60+m;render();},initial/60,initial%60,true).show();
        });
        action(moreOptions?"Less options":"More options",()->{remember();moreOptions=!moreOptions;render();});
        main.addView(advanced);advanced.setVisibility(moreOptions?View.VISIBLE:View.GONE);body=advanced;
        if(run==null) text("Date: "+date,14);
        else if(run.label.equals("ND"))text("Times after midnight use the next morning.",14);
        if(minute>=0) action("Remove time",()->{remember();minute=-1;reminder=0;repeat=0;render();});
        text("Reminder · Optional",15);
        reminders=spinner(new String[]{"No reminder","At task time","5 minutes before","10 minutes before","15 minutes before","30 minutes before","1 hour before"},reminder);
        text("Repeat · Optional",15);
        if(taskRun==0) {
            repeats=new Spinner(this);RepeatRules.bind(this,repeats,repeat);body.addView(repeats);
            action("Selected weekdays…",()->{remember();RepeatRules.weekdays(this,repeat,code->{repeat=code;render();});});
            action("Custom repeat…",()->{remember();RepeatRules.custom(this,repeat,code->{repeat=code;render();});});
        } else repeats=spinner(new String[]{"Never","Every 30 minutes","Every hour","Every 2 hours","Every 3 hours","Every 4 hours"},repeat);
        body=main;
        action("Save task",()->saveTask());
    }
    private void saveTask() {
        remember();
        try {
            if(draft.trim().isEmpty()) { name.setError("Enter a task name"); return; }
            if(minute<0 && (reminder>0 || repeat>0)) throw new IllegalArgumentException("Choose a time for reminders or repeats");
            if(taskRun>0) repository.addTask(taskRun,draft,minute,INTERVALS[repeat],EARLY[reminder]);
            else {
                long due=minute<0?0:date.atTime(minute/60,minute%60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if(due>0 && due<=System.currentTimeMillis()) throw new IllegalArgumentException("Choose a future time");
                try(TaskStore store=new TaskStore(this)) { store.add(draft,1,due,repeat,EARLY[reminder]); }
            }
            boolean wantsReminder=reminder>0;
            draft=""; minute=-1; reminder=0; repeat=0; moreOptions=false; screen=0; render();
            TaskNotifications.syncAll(this); ReminderScheduler.rescheduleAll(this);
            if(wantsReminder && !TaskNotifications.canNotify(this)) {
                if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);
                else toast("Enable PinTask notifications in Android settings for reminders");
            }
            toast("Task saved");
        } catch(IllegalArgumentException e) { toast(e.getMessage()); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        TaskNotifications.syncAll(this); ReminderScheduler.rescheduleAll(this);
        if(!TaskNotifications.canNotify(this)) toast("Task saved. Enable notifications in Android settings for reminders.");
    }
    private void showTasks(List<TaskStore.Task> tasks) {
        tasks=TaskGroups.filter(this,tasks);
        for(TaskStore.Task task:tasks) {
            if(task.done)continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(6));
            CheckBox done=new CheckBox(this);done.setContentDescription("Complete "+task.title);done.setEnabled(!task.paused);row.addView(done);
            LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);
            TextView title=new TextView(this);title.setText(task.title);title.setTextColor(FG);title.setTextSize(17);detail.addView(title);title.setOnClickListener(v->TaskDetails.open(this,task,this::render));
            String meta=(task.dueAt==0?"":java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new Date(task.dueAt))+" · ")+(task.group.isEmpty()?"":task.group+" · ")+TaskDetails.summary(task);
            if(!meta.isEmpty()){TextView time=new TextView(this);time.setText(meta.replaceAll(" · $",""));time.setTextColor(Color.LTGRAY);time.setTextSize(12);detail.addView(time);}
            row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));row.addView(TaskCards.overflow(this,task,this::render));body.addView(row);
            done.setOnCheckedChangeListener((v,checked)->{if(!checked)return;boolean completed;
                try(TaskStore store=new TaskStore(this)){TaskStore.Completion result=store.complete(task.id);completed=result!=null;if(completed){ReminderScheduler.cancel(this,task.id);TaskNotifications.cancel(this,task.id);}}
                TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);render();if(completed)undoBar.offer(task.id,this::render);
            });
        }
    }
    private String summary(ShiftRepository.Run run) {
        if(run.templateId==0 && (run.label.equals("Off")||run.label.equals("EZ"))) return (run.group.isEmpty()?"":run.group+" · ")+run.label;
        DateTimeFormatter format=DateTimeFormatter.ofPattern("HH:mm");
        return (run.group.isEmpty()?"":run.group+" · ")+run.label+" · "+Instant.ofEpochMilli(run.startsAt).atZone(ZoneId.systemDefault()).format(format)+" – "+Instant.ofEpochMilli(run.endsAt).atZone(ZoneId.systemDefault()).format(format)+(dateOf(run.startsAt).equals(dateOf(run.endsAt))?"":" (+1 day)");
    }
    private LocalDate dateOf(long value) { return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate(); }
    private void text(String value,int size) { TextView v=new TextView(this);v.setText(value);v.setTextColor(FG);v.setTextSize(size);v.setPadding(4,12,4,8);body.addView(v); }
    private void action(String label,Runnable action) { Button v=new Button(this);v.setText(label);v.setAllCaps(false);body.addView(v);v.setOnClickListener(w->action.run()); }
    private Spinner spinner(String[] labels,int index) { Spinner v=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);v.setAdapter(a);v.setSelection(Math.min(index,labels.length-1));body.addView(v);return v; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
