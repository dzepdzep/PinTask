package com.pintask.app;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Three destinations: everyday Tasks, Work, and advanced More. */
public final class ShiftActivity extends Activity {
    private static final int TASKS=0, WORK=1, MORE=2, EDITOR=3, HISTORY=4, PLAN=5;
    private static final int BG=Color.rgb(12,18,30),SURFACE=Color.rgb(24,34,51),BORDER=Color.rgb(43,57,76);
    private static final int FG=Color.rgb(238,244,251),MUTED=Color.rgb(156,173,194),ACCENT=Color.rgb(166,233,205);
    private static final int[] EARLY={-2,0,5,10,15,30,60},INTERVALS={0,30,60,120,180,240};
    private ShiftRepository repository;
    private LinearLayout body;
    private UndoBar undoBar;
    private int screen=TASKS,subpage=0;
    private LocalDate date=LocalDate.now(),editorDate=LocalDate.now();
    private long taskRun=0,detailRun=0;
    private String draft="",quickDraft="",editorGroup="";
    private int minute=-1,reminder=0,repeat=0;
    private boolean moreOptions,workEditor,copyingDay;
    private String planCode="TD1";
    private final List<LocalDate> planDates=new ArrayList<>();
    private EditText name,quickEntry;
    private Spinner reminders,repeats;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);repository=new ShiftRepository(this);
        if(state!=null){screen=state.getInt("screen");subpage=state.getInt("subpage");date=LocalDate.parse(state.getString("date"));editorDate=LocalDate.parse(state.getString("editorDate",LocalDate.now().toString()));taskRun=state.getLong("run");detailRun=state.getLong("detailRun");draft=state.getString("draft","");quickDraft=state.getString("quickDraft","");editorGroup=state.getString("editorGroup","");minute=state.getInt("minute",-1);reminder=state.getInt("reminder");repeat=state.getInt("repeat");moreOptions=state.getBoolean("moreOptions");workEditor=state.getBoolean("workEditor");}
        if(state!=null){copyingDay=state.getBoolean("copyingDay");planCode=state.getString("planCode","TD1");ArrayList<String> saved=state.getStringArrayList("planDates");if(saved!=null)for(String d:saved)planDates.add(LocalDate.parse(d));}
        if(state==null)routeIntent(getIntent());render();
    }
    @Override protected void onResume(){super.onResume();TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);if(repository!=null && screen!=EDITOR){remember();render();}}
    @Override protected void onDestroy(){repository.close();super.onDestroy();}
    private void routeIntent(Intent intent){
        detailRun=0;
        long id=intent.getLongExtra("open_task",0);
        if(id>0){try(TaskStore store=new TaskStore(this)){TaskStore.Task task=store.find(id);if(task!=null){if(TaskSections.isWork(task)){TaskGroups.select(this,task.group);ShiftRepository.Run run=task.shiftRunId>0?repository.findRun(task.shiftRunId):null;if(run!=null)date=dateOf(run.startsAt);screen=task.done?HISTORY:WORK;}else screen=task.done?HISTORY:TASKS;}}}
        else if(intent.getBooleanExtra("open_work",false))screen=WORK;
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);remember();setIntent(intent);routeIntent(intent);render();}
    private void remember(){if(quickEntry!=null)quickDraft=quickEntry.getText().toString();if(name!=null){draft=name.getText().toString();reminder=reminders.getSelectedItemPosition();repeat=taskRun==0?RepeatRules.selected(repeats):repeats.getSelectedItemPosition();}}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putBoolean("copyingDay",copyingDay);state.putString("planCode",planCode);ArrayList<String> savedDates=new ArrayList<>();for(LocalDate d:planDates)savedDates.add(d.toString());state.putStringArrayList("planDates",savedDates);state.putInt("screen",screen);state.putInt("subpage",subpage);state.putString("date",date.toString());state.putString("editorDate",editorDate.toString());state.putLong("run",taskRun);state.putLong("detailRun",detailRun);state.putString("draft",draft);state.putString("quickDraft",quickDraft);state.putString("editorGroup",editorGroup);state.putInt("minute",minute);state.putInt("reminder",reminder);state.putInt("repeat",repeat);state.putBoolean("moreOptions",moreOptions);state.putBoolean("workEditor",workEditor);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){remember();if(screen==EDITOR)screen=workEditor?WORK:TASKS;else if(screen==HISTORY)screen=MORE;else if(screen==PLAN)screen=WORK;else if(screen==WORK && detailRun!=0)detailRun=0;else if(screen!=TASKS)screen=TASKS;else{super.onBackPressed();return;}render();}
    private void navigate(int page){remember();screen=page;subpage=0;detailRun=0;render();}
    private void render(){
        name=null;quickEntry=null;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());int keyboard=insets.getInsets(WindowInsets.Type.ime()).bottom;v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard));return insets;});else root.setFitsSystemWindows(true);
        setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(body);
        if(screen==TASKS)tasksPage();else if(screen==WORK)workPage();else if(screen==MORE)morePage();else if(screen==HISTORY)historyPage();else if(screen==PLAN)planPage();else addTask();
        undoBar=new UndoBar(this);root.addView(undoBar);
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(12),dp(8),dp(12),dp(10));nav.setBackgroundColor(Color.rgb(16,24,38));root.addView(nav);
        String[] labels={"Tasks","Work","More"};int selected=screen==EDITOR?(workEditor?WORK:TASKS):screen==HISTORY?MORE:screen==PLAN?WORK:screen;
        for(int i=0;i<labels.length;i++){final int page=i;TextView tab=new TextView(this);tab.setText(labels[i]);tab.setGravity(Gravity.CENTER);tab.setTextSize(15);tab.setTypeface(Typeface.DEFAULT,selected==i?Typeface.BOLD:Typeface.NORMAL);tab.setTextColor(selected==i?ACCENT:MUTED);tab.setBackground(shape(selected==i?SURFACE:Color.TRANSPARENT,14));tab.setContentDescription(labels[i]+" page");tab.setFocusable(true);tab.setSelected(selected==i);nav.addView(tab,new LinearLayout.LayoutParams(0,dp(52),1));tab.setOnClickListener(v->navigate(page));}
    }
    private void tasksPage(){
        heading("Tasks","Your everyday to-do list");
        LinearLayout compose=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);compose.addView(row);
        quickEntry=new EditText(this);quickEntry.setSingleLine(true);quickEntry.setText(quickDraft);quickEntry.setHint("What needs doing?");quickEntry.setTextColor(FG);quickEntry.setHintTextColor(MUTED);quickEntry.setTextSize(16);quickEntry.setBackgroundColor(Color.TRANSPARENT);quickEntry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});row.addView(quickEntry,new LinearLayout.LayoutParams(0,dp(52),1));
        Button add=button("Add",true);row.addView(add,new LinearLayout.LayoutParams(dp(78),dp(48)));add.setOnClickListener(v->quickAdd());
        quickEntry.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);quickEntry.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){quickAdd();return true;}return false;});
        link("Add with time or repeat",()->{remember();if(!quickDraft.isEmpty()){draft=quickDraft;quickDraft="";quickEntry.setText("");}openEditor(false,0);});
        try(TaskStore store=new TaskStore(this)){List<TaskStore.Task> tasks=TaskSections.personal(store.activeAll());if(tasks.isEmpty())empty("A little space to breathe","Add your first task above.");else{section(tasks.size()+" to do");showTasks(tasks);}}
    }
    private void quickAdd(){
        String title=quickEntry.getText().toString().trim();if(title.isEmpty()){quickEntry.setError("Enter a task name");return;}
        try(TaskStore store=new TaskStore(this)){TaskSections.addPersonal(store,title);}TaskWidget.refreshAll(this);quickDraft="";hideKeyboard();render();
    }
    private void workPage(){
        if(detailRun>0){shiftTasksPage();return;}
        heading("Work","Shifts, jobs and patients");
        String group=TaskGroups.selected(this);action("Job / patient: "+(group==null?"All work":group.isEmpty()?"Unassigned":group),()->{remember();TaskGroups.chooseWork(this,()->{taskRun=0;render();});});
        List<ShiftRepository.Run> plans=repository.runsForDate(date);
        ShiftRepository.Run current=repository.currentRun();if(date.equals(LocalDate.now()) && current!=null && !dateOf(current.startsAt).equals(date))plans.add(0,current);
        LinearLayout shift=card();LinearLayout previous=body;body=shift;
        text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),19);
        text("Choose a day, then open a shift below for its tasks.",13);
        action("Set a shift",this::pickWorkDate);
        link("Next shift",this::openNextShift);
        link("Copy day / plan several days",this::planningMenu);body=previous;
        for(ShiftRepository.Run plan:plans)tool(summary(plan),plan.dayLabel?"No shift":"Open tasks · "+dateOf(plan.startsAt).format(DateTimeFormatter.ofPattern("d MMM")),()->{if(plan.dayLabel)shiftMenu(plan);else{detailRun=plan.id;render();}});
        primary("Add work task",()->{List<ShiftRepository.Run> choices=new ArrayList<>();for(ShiftRepository.Run r:plans)if(working(r) && r.endsAt>System.currentTimeMillis())choices.add(r);if(choices.size()<2){openEditor(true,choices.isEmpty()?0:choices.get(0).id);return;}String[] labels=new String[choices.size()+1];labels[0]="Job / patient task (no shift)";for(int i=0;i<choices.size();i++)labels[i+1]=summary(choices.get(i));new AlertDialog.Builder(this).setTitle("Which shift?").setItems(labels,(d,i)->openEditor(true,i==0?0:choices.get(i-1).id)).show();});
        try(TaskStore store=new TaskStore(this)){List<TaskStore.Task> tasks=TaskSections.work(this,store.activeAll());if(tasks.isEmpty())empty("Work tasks live here","Choose a job or patient, or add a task to a shift.");else{section(tasks.size()+" work tasks");showTasks(tasks);}}
    }
    private void openNextShift() {
        ShiftRepository.Run next=repository.nextRun();if(next==null){toast("No upcoming shift for this job/patient");return;}
        remember();date=dateOf(next.startsAt);detailRun=next.id;screen=WORK;render();
    }
    private void planningMenu() {
        new AlertDialog.Builder(this).setTitle("Plan work days").setItems(new String[]{"Copy this day to other dates","Assign a shift to several dates"},(d,i)->{remember();copyingDay=i==0;planDates.clear();planCode="TD1";screen=PLAN;render();}).setNegativeButton("Cancel",null).show();
    }
    private void planPage() {
        heading(copyingDay?"Copy day":"Plan several days",copyingDay?"Copy shifts and tasks from "+date.format(DateTimeFormatter.ofPattern("EEE, d MMM")):"Choose a shift and the dates you work it");
        link("‹ Back to Work",()->{screen=WORK;render();});
        String group=copyingDay?TaskGroups.selected(this):TaskGroups.newTaskGroup(this);
        text("Job / patient: "+(group==null?"All work":group.isEmpty()?"Unassigned":group),14);
        if(copyingDay)text("Copies the current job/patient selection into empty days. Completed tasks and checklists start fresh. Tasks with times already past are skipped.",14);
        else action("Shift: "+planCode,()->new AlertDialog.Builder(this).setTitle("Choose shift").setItems(new String[]{"TD1 · 08:00–20:00","ND · 20:00–08:00 next day","TD2 · 09:00–21:00","Off"},(d,i)->{planCode=new String[]{"TD1","ND","TD2","Off"}[i];render();}).show());
        action("Add date",()->{if(planDates.size()>=31){toast("Choose up to 31 dates at once");return;}LocalDate initial=planDates.isEmpty()?LocalDate.now():planDates.get(planDates.size()-1).plusDays(1);DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{LocalDate chosen=LocalDate.of(y,m+1,d);if(chosen.isBefore(LocalDate.now()) || (copyingDay && chosen.equals(date))){toast("Choose today or a future date different from the source day");return;}if(!planDates.contains(chosen))planDates.add(chosen);Collections.sort(planDates);render();},initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());picker.getDatePicker().setMinDate(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();});
        for(LocalDate chosen:planDates)link(chosen.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))+" · remove",()->{planDates.remove(chosen);render();});
        if(planDates.isEmpty())text("Add one or more dates above (up to 31).",14);
        primary((copyingDay?"Copy to ":"Apply to ")+planDates.size()+(planDates.size()==1?" day":" days"),()->applyPlan(false));
    }
    private void applyPlan(boolean overlap) {
        try {
            if(copyingDay)repository.copyDay(date,planDates,overlap);else repository.scheduleDates(planDates,planCode,overlap);
            date=planDates.get(0);detailRun=0;screen=WORK;planDates.clear();TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);TaskWidget.refreshAll(this);render();toast("Days saved");
        } catch(IllegalArgumentException e) {
            if(!overlap && e.getMessage().startsWith("This overlaps"))new AlertDialog.Builder(this).setTitle("Shifts overlap").setMessage("Some selected dates overlap existing shifts. Keep both? All selected dates will be applied together.").setNegativeButton("Cancel",null).setPositiveButton("Keep both",(d,i)->applyPlan(true)).show();else toast(e.getMessage());
        }
    }
    private void pickWorkDate(){
        DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{date=LocalDate.of(y,m+1,d);render();chooseShift();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());picker.setTitle("Choose work day");picker.show();
    }
    private void openEditor(boolean work,long run){
        remember();if(draft.isEmpty() || workEditor!=work)editorDate=work?date:LocalDate.now();if(run>0){ShiftRepository.Run chosen=repository.findRun(run);if(chosen!=null)editorDate=dateOf(chosen.startsAt);}if(workEditor!=work || (taskRun>0)!=(run>0))repeat=0;workEditor=work;taskRun=run;editorGroup=work?TaskGroups.newTaskGroup(this):"";screen=EDITOR;render();
    }
    private void morePage(){
        heading("More","Extra tools, whenever you need them");
        section("ORGANIZE");tool("Task library & search","Find tasks across every group",()->startActivity(new Intent(this,MainActivity.class).putExtra("search_all",true)));
        tool("Completed tasks","Your finished task history",()->{screen=HISTORY;render();});tool("Task bundles","Reuse a set of tasks",()->TaskBundles.show(this,this::render));
        section("WORK SETUP");tool("Saved shift templates","Optional templates and task rules",()->startActivity(new Intent(this,LegacyShiftActivity.class)));
        section("SETTINGS");tool("Quick Add notification",TaskNotifications.quickAddEnabled(this)?"On · tap to change":"Off · optional notification shortcut",this::quickSettings);
        tool("Backup & restore","Export or restore your saved data",()->startActivity(new Intent(this,BackupActivity.class)));
        text("PinTask 2.3",12);
    }
    private void quickSettings(){new AlertDialog.Builder(this).setTitle("Quick Add notification").setSingleChoiceItems(new String[]{"Off","On"},TaskNotifications.quickAddEnabled(this)?1:0,(d,index)->{TaskNotifications.setQuickAddEnabled(this,index==1);d.dismiss();render();if(index==1 && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);}).setNegativeButton("Cancel",null).show();}
    private void historyPage(){heading("Completed tasks","Your task history");link("‹ Back to More",()->navigate(MORE));try(TaskStore store=new TaskStore(this)){List<TaskStore.Task> done=store.completedAll();if(done.isEmpty())empty("Nothing completed yet","Finished tasks will appear here.");for(TaskStore.Task task:done)body.addView(TaskCards.done(this,task,this::render));}}
    private void chooseShift() {
        String[] codes={"TD1","ND","TD2","Off"};
        new AlertDialog.Builder(this).setTitle("Add shift or day label · "+date.format(DateTimeFormatter.ofPattern("d MMM")))
            .setItems(new String[]{"TD1 · 08:00–20:00","ND · 20:00–08:00 next day","TD2 · 09:00–21:00","Off · no shift"},(d,index)->{
                try{repository.schedule(date,codes[index]);render();}catch(IllegalArgumentException e){if(e.getMessage().startsWith("This overlaps"))new AlertDialog.Builder(this).setTitle("Shifts overlap").setMessage("These shifts have overlapping hours. Keep both on this date?").setNegativeButton("Cancel",null).setPositiveButton("Keep both",(a,b)->{repository.schedule(date,codes[index],true);render();}).show();else toast(e.getMessage());}
            }).setNegativeButton("Cancel",null).show();
    }
    private boolean working(ShiftRepository.Run run) {return !run.dayLabel;}
    private void shiftMenu(ShiftRepository.Run run) {
        new AlertDialog.Builder(this).setTitle(summary(run)).setItems(new String[]{"Add shift or day label","Open this shift","End / clear shift"},(d,index)->{
            if(index==0){chooseShift();return;}
            if(index==1){if(!run.dayLabel)detailRun=run.id;render();return;}
            new AlertDialog.Builder(this).setTitle("End "+run.label+"?").setMessage("Unfinished tasks and reminders will be canceled. Completed history stays saved.")
                .setNegativeButton("Keep",null).setPositiveButton("End shift",(a,b)->{for(long id:repository.endShift(run.id)){ReminderScheduler.cancel(this,id);TaskNotifications.cancel(this,id);}render();}).show();
        }).setNegativeButton("Close",null).show();
    }
    private void shiftTasksPage() {
        ShiftRepository.Run run=repository.findRun(detailRun);
        if(run==null || run.dayLabel){detailRun=0;workPage();return;}
        heading(run.label,dateOf(run.startsAt).format(DateTimeFormatter.ofPattern("EEE, d MMM"))+" · "+summary(run));
        link("‹ Back to Work",()->{detailRun=0;render();});
        link("Shift options",()->shiftMenu(run));
        primary("Add task to this shift",()->openEditor(true,run.id));
        action("Use saved template",()->useTemplate(run));
        List<TaskStore.Task> tasks=repository.runTasks(run.id);
        if(tasks.isEmpty())empty("No tasks yet","Add tasks here, or use an optional saved template.");
        else{section("SHIFT TASKS");showTasks(tasks);for(TaskStore.Task task:tasks)if(task.done)body.addView(TaskCards.done(this,task,this::render));}
        action("Save as template",()->saveTemplate(run));
    }
    private void saveTemplate(ShiftRepository.Run run) {
        EditText title=new EditText(this);title.setSingleLine(true);title.setText(getString(R.string.shift_template_name,run.label));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Save as template").setMessage("Reuse this shift's tasks, times, reminders, notes and checklists. Completed tasks are included and reset when reused.").setView(title).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{repository.saveRunAsTemplate(run.id,title.getText().toString());dialog.dismiss();toast("Template saved");}catch(IllegalArgumentException e){title.setError(e.getMessage());}}));dialog.show();
    }
    private void useTemplate(ShiftRepository.Run run) {
        List<ShiftRepository.Template> templates=repository.templates();if(templates.isEmpty()){toast("Save a shift as a template first");return;}
        String[] labels=new String[templates.size()];for(int i=0;i<labels.length;i++)labels[i]=templates.get(i).name;
        new AlertDialog.Builder(this).setTitle("Use saved template").setItems(labels,(d,i)->new AlertDialog.Builder(this).setTitle("Add "+labels[i]+"?").setMessage("Adds the template's tasks to this shift. Existing tasks stay saved; applying it again adds another copy.").setNegativeButton("Cancel",null).setPositiveButton("Add tasks",(a,b)->{try{repository.applyTemplate(run.id,templates.get(i).id);TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);render();}catch(IllegalArgumentException e){toast(e.getMessage());}}).show()).setNegativeButton("Cancel",null).show();
    }
    private void addTask() {
        heading(workEditor?"New work task":"New task",workEditor?"For your job, patient or shift":"For your everyday to-do list");
        link("‹ Back to "+(workEditor?"Work":"Tasks"),()->{remember();screen=workEditor?1:0;render();});
        ShiftRepository.Run run=repository.findRun(taskRun);
        if(run==null) taskRun=0;
        if(workEditor)text(run==null?(editorGroup.isEmpty()?"Work":editorGroup):summary(run),15);
        LinearLayout main=body;
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);
        body=main;
        name=new EditText(this); name.setSingleLine(true); name.setTextColor(FG); name.setHintTextColor(Color.LTGRAY);
        name.setHint("Task name"); name.setText(draft);name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);name.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){saveTask();return true;}return false;}); name.setPadding(dp(14),dp(12),dp(14),dp(12));name.setBackground(shape(SURFACE,12));name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)}); body.addView(name);
        if(run==null)text("Date: "+editorDate,14);
        action(workEditor?"Change date or shift":"Change date",()->{
            remember();
            new DatePickerDialog(this,(v,y,m,d)->{
                editorDate=LocalDate.of(y,m+1,d);
                if(!workEditor){render();return;}
                List<ShiftRepository.Run> choices=new ArrayList<>();
                for(ShiftRepository.Run r:repository.runsForDate(editorDate)) if(working(r)) choices.add(r);
                String[] items=new String[choices.size()+1]; items[0]="Job / patient task (no shift)";
                for(int i=0;i<choices.size();i++) items[i+1]=summary(choices.get(i));
                new AlertDialog.Builder(this).setTitle("Add task for "+editorDate).setItems(items,(dialog,index)->{taskRun=index==0?0:choices.get(index-1).id;repeat=0;render();}).show();
            },editorDate.getYear(),editorDate.getMonthValue()-1,editorDate.getDayOfMonth()).show();
        });
        action(minute<0?"Time · Optional":("Time · "+String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60)),()->{
            remember(); int initial=minute<0?LocalTime.now().getHour()*60:minute;
            new TimePickerDialog(this,(v,h,m)->{minute=h*60+m;render();},initial/60,initial%60,true).show();
        });
        link(moreOptions?"Less options":"More options",()->{remember();moreOptions=!moreOptions;render();});
        main.addView(advanced);advanced.setVisibility(moreOptions?View.VISIBLE:View.GONE);body=advanced;
        if(run!=null && run.label.equals("ND"))text("Times after midnight use the next morning.",14);
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
        primary("Save task",this::saveTask);
    }
    private void saveTask() {
        remember();
        try {
            if(draft.trim().isEmpty()) { name.setError("Enter a task name"); return; }
            if(minute<0 && (reminder>0 || repeat>0)) throw new IllegalArgumentException("Choose a time for reminders or repeats");
            if(taskRun>0) repository.addTask(taskRun,draft,minute,INTERVALS[repeat],EARLY[reminder]);
            else {
                long due=minute<0?0:editorDate.atTime(minute/60,minute%60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if(due>0 && due<=System.currentTimeMillis()) throw new IllegalArgumentException("Choose a future time");
                try(TaskStore store=new TaskStore(this)) { android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{TaskStore.Task created=store.add(draft,1,due,repeat,EARLY[reminder]);store.setGroup(created.id,workEditor?(editorGroup.isEmpty()?"Work":editorGroup):"");db.setTransactionSuccessful();}finally{db.endTransaction();} }
            }
            if(workEditor && taskRun==0 && editorGroup.isEmpty() && "".equals(TaskGroups.selected(this)))TaskGroups.select(this,"Work");
            boolean wantsReminder=reminder>0;
            hideKeyboard();
            draft=""; minute=-1; reminder=0; repeat=0; moreOptions=false;if(workEditor)detailRun=taskRun; screen=workEditor?1:0; render();
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
        if(!TaskNotifications.canNotify(this))toast(request==42?"Enable notifications in Android settings for Quick Add.":"Enable notifications in Android settings for reminders.");
    }
    private void showTasks(List<TaskStore.Task> tasks) {
        for(TaskStore.Task task:tasks) {
            if(task.done)continue;
            LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(4),dp(8));
            CheckBox done=new CheckBox(this);done.setContentDescription("Complete "+task.title);done.setMinWidth(dp(48));done.setMinHeight(dp(48));done.setEnabled(!task.paused);row.addView(done);
            LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);
            TextView title=new TextView(this);title.setText(task.title);title.setTextColor(FG);title.setTextSize(17);detail.addView(title);title.setOnClickListener(v->TaskDetails.open(this,task,this::render));
            ShiftRepository.Run linked=task.shiftRunId>0?repository.findRun(task.shiftRunId):null;
            String shiftInfo=linked==null?"":linked.label+" · "+dateOf(linked.startsAt).format(DateTimeFormatter.ofPattern("d MMM"))+" · ";
            String meta=shiftInfo+(task.dueAt==0?"":dueLabel(task.dueAt)+" · ")+(task.group.isEmpty()?"":task.group+" · ")+TaskDetails.summary(task);
            if(!meta.isEmpty()){TextView time=new TextView(this);time.setText(meta.replaceAll(" · $",""));time.setTextColor(Color.LTGRAY);time.setTextSize(12);detail.addView(time);}
            row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));row.addView(TaskCards.overflow(this,task,this::render));
            done.setOnCheckedChangeListener((v,checked)->{if(!checked)return;boolean completed;
                try(TaskStore store=new TaskStore(this)){TaskStore.Completion result=store.complete(task.id);completed=result!=null;if(completed){ReminderScheduler.cancel(this,task.id);TaskNotifications.cancel(this,task.id);}}
                TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);render();if(completed)undoBar.offer(task.id,this::render);
            });
        }
    }
    private String summary(ShiftRepository.Run run) {
        if(run.dayLabel) return (run.group.isEmpty()?"":run.group+" · ")+"Off";
        DateTimeFormatter format=DateTimeFormatter.ofPattern("HH:mm");
        return (run.group.isEmpty()?"":run.group+" · ")+run.label+" · "+Instant.ofEpochMilli(run.startsAt).atZone(ZoneId.systemDefault()).format(format)+" – "+Instant.ofEpochMilli(run.endsAt).atZone(ZoneId.systemDefault()).format(format)+(dateOf(run.startsAt).equals(dateOf(run.endsAt))?"":" (+1 day)");
    }
    private LocalDate dateOf(long value) { return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate(); }
    private String dueLabel(long due){java.time.ZonedDateTime time=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault());String clock=time.format(DateTimeFormatter.ofPattern("HH:mm"));LocalDate today=LocalDate.now();if(time.toLocalDate().equals(today))return clock;if(time.toLocalDate().equals(today.plusDays(1)))return "Tomorrow · "+clock;return time.format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm"));}
    private void heading(String title,String subtitle){TextView brand=text("P I N T A S K",11);brand.setTextColor(ACCENT);brand.setLetterSpacing(.12f);TextView heading=text(title,32);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);text(subtitle,14);}
    private void section(String label){TextView title=text(label,12);title.setTextColor(MUTED);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setPadding(dp(2),dp(24),dp(2),dp(10));}
    private LinearLayout card(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(14),dp(12),dp(14),dp(12));view.setBackground(shape(SURFACE,16));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);body.addView(view,params);return view;}
    private void empty(String title,String subtitle){LinearLayout previous=body;body=card();text(title,18);text(subtitle,14);body=previous;}
    private void tool(String title,String subtitle,Runnable action){LinearLayout box=card();TextView name=new TextView(this);name.setText(title+"  ›");name.setTextColor(FG);name.setTextSize(17);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(name);TextView description=new TextView(this);description.setText(subtitle);description.setTextColor(MUTED);description.setTextSize(13);description.setPadding(0,dp(6),0,0);box.addView(description);box.setFocusable(true);box.setContentDescription(title);box.setOnClickListener(v->action.run());}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextColor(size<=14?MUTED:FG);view.setTextSize(size);view.setPadding(dp(2),dp(8),dp(2),dp(6));body.addView(view);return view;}
    private GradientDrawable shape(int fill,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(fill);bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),fill==Color.TRANSPARENT?Color.TRANSPARENT:BORDER);return bg;}
    private Button button(String label,boolean primary){Button view=new Button(this);view.setText(label);view.setAllCaps(false);view.setTextSize(15);view.setTextColor(primary?BG:FG);view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);view.setBackground(shape(primary?ACCENT:SURFACE,12));view.setPadding(dp(14),dp(8),dp(14),dp(8));return view;}
    private void action(String label,Runnable action){addButton(label,action,false);}
    private void primary(String label,Runnable action){addButton(label,action,true);}
    private void addButton(String label,Runnable action,boolean primary){Button view=button(label,primary);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);params.bottomMargin=dp(4);view.setMinHeight(dp(50));body.addView(view,params);view.setOnClickListener(v->action.run());}
    private void link(String label,Runnable action){TextView view=text(label,14);view.setTextColor(ACCENT);view.setMinHeight(dp(48));view.setGravity(Gravity.CENTER_VERTICAL);view.setFocusable(true);view.setOnClickListener(v->action.run());}
    private Spinner spinner(String[] labels,int index){Spinner view=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);view.setAdapter(adapter);view.setSelection(Math.min(index,labels.length-1));body.addView(view);return view;}
    private void hideKeyboard(){android.view.inputmethod.InputMethodManager input=getSystemService(android.view.inputmethod.InputMethodManager.class);if(input!=null)input.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
