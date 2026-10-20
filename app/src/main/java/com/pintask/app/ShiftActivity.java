package com.pintask.app;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.content.ActivityNotFoundException;
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
    private static final int REQUEST_VOICE_TASK=93;
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
    private int quickDelaySeconds=0, editorDelaySeconds=-1;
    private boolean moreOptions,workEditor,copyingDay;
    private long planTemplateId=0; // 0 = not selected; -1 = Off; positive = saved custom type
    private final List<LocalDate> planDates=new ArrayList<>();
    private EditText name,quickEntry;
    private Spinner reminders,repeats;
    private StartupAccess startupAccess;
    private ShiftGuide tour;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);repository=new ShiftRepository(this);tour=new ShiftGuide(this);startupAccess=new StartupAccess(this);
        if(state!=null){screen=state.getInt("screen");subpage=state.getInt("subpage");date=LocalDate.parse(state.getString("date"));editorDate=LocalDate.parse(state.getString("editorDate",LocalDate.now().toString()));taskRun=state.getLong("run");detailRun=state.getLong("detailRun");draft=state.getString("draft","");quickDraft=state.getString("quickDraft","");editorGroup=state.getString("editorGroup","");minute=state.getInt("minute",-1);quickDelaySeconds=state.getInt("quickDelaySeconds",0);editorDelaySeconds=state.getInt("editorDelaySeconds",-1);reminder=state.getInt("reminder");repeat=state.getInt("repeat");moreOptions=state.getBoolean("moreOptions");workEditor=state.getBoolean("workEditor");}
        if(state!=null){copyingDay=state.getBoolean("copyingDay");planTemplateId=state.getLong("planTemplateId",0);ArrayList<String> saved=state.getStringArrayList("planDates");if(saved!=null)for(String d:saved)planDates.add(LocalDate.parse(d));}
        if(state!=null)tour.restore(state.getBoolean("interactiveTour",false),state.getInt("tourStage",0));
        if(state==null)routeIntent(getIntent());render();
        if(state==null)startupAccess.begin(()->ShiftGuide.maybeWelcome(this));
    }
    @Override protected void onResume(){super.onResume();TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);if(repository!=null && screen!=EDITOR){remember();render();}
        if(startupAccess!=null)startupAccess.onResume();}
    @Override protected void onDestroy(){if(tour!=null)tour.stop();repository.close();super.onDestroy();}
    private void routeIntent(Intent intent){
        detailRun=0;
        long id=intent.getLongExtra("open_task",0);
        if(id>0){try(TaskStore store=new TaskStore(this)){TaskStore.Task task=store.find(id);if(task!=null){if(TaskSections.isWork(task)){TaskGroups.select(this,task.group);ShiftRepository.Run run=task.shiftRunId>0?repository.findRun(task.shiftRunId):null;if(run!=null)date=dateOf(run.startsAt);screen=task.done?HISTORY:WORK;}else screen=task.done?HISTORY:TASKS;}}}
        else if(intent.getBooleanExtra("open_work",false))screen=WORK;
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);remember();setIntent(intent);routeIntent(intent);render();}
    private void remember(){if(quickEntry!=null)quickDraft=quickEntry.getText().toString();if(name!=null){draft=name.getText().toString();reminder=reminders.getSelectedItemPosition();repeat=taskRun==0?RepeatRules.selected(repeats):repeats.getSelectedItemPosition();}}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putBoolean("copyingDay",copyingDay);state.putLong("planTemplateId",planTemplateId);ArrayList<String> savedDates=new ArrayList<>();for(LocalDate d:planDates)savedDates.add(d.toString());state.putStringArrayList("planDates",savedDates);state.putInt("screen",screen);state.putInt("subpage",subpage);state.putString("date",date.toString());state.putString("editorDate",editorDate.toString());state.putLong("run",taskRun);state.putLong("detailRun",detailRun);state.putString("draft",draft);state.putString("quickDraft",quickDraft);state.putString("editorGroup",editorGroup);state.putInt("minute",minute);state.putInt("quickDelaySeconds",quickDelaySeconds);state.putInt("editorDelaySeconds",editorDelaySeconds);state.putInt("reminder",reminder);state.putInt("repeat",repeat);state.putBoolean("moreOptions",moreOptions);state.putBoolean("workEditor",workEditor);
        state.putBoolean("interactiveTour",tour!=null&&tour.active());
        state.putInt("tourStage",tour==null?0:tour.stage());
        super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(tour!=null&&tour.active())tour.stop();remember();if(screen==EDITOR)screen=workEditor?WORK:TASKS;else if(screen==HISTORY)screen=MORE;else if(screen==PLAN)screen=WORK;else if(screen==WORK && detailRun!=0)detailRun=0;else if(screen!=TASKS)screen=TASKS;else{super.onBackPressed();return;}render();}
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
        if(tour!=null)tour.onRender(root);
    }

    void tourStart(){tour.start();}
    void tourOpenTasks(){navigate(TASKS);}
    void tourRefresh(){render();}
    EditText tourTitleField(){return name;}
    Spinner tourReminderSelector(){return reminders;}
    private void tasksPage(){
        heading("Tasks","Your everyday to-do list");
        LinearLayout compose=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);compose.addView(row);
        quickEntry=new EditText(this);quickEntry.setSingleLine(true);quickEntry.setText(quickDraft);quickEntry.setHint("What needs doing?");quickEntry.setTextColor(FG);quickEntry.setHintTextColor(MUTED);quickEntry.setTextSize(16);quickEntry.setBackgroundColor(Color.TRANSPARENT);quickEntry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});row.addView(quickEntry,new LinearLayout.LayoutParams(0,dp(52),1));
        Button microphone=button("🎤",false);
        microphone.setContentDescription("Speak a task");
        row.addView(microphone,new LinearLayout.LayoutParams(dp(54),dp(48)));
        microphone.setOnClickListener(v->startVoiceTask());
        Button add=button("Add",true);row.addView(add,new LinearLayout.LayoutParams(dp(70),dp(48)));add.setOnClickListener(v->quickAdd());
        quickEntry.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);quickEntry.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){quickAdd();return true;}return false;});
        action("🎤 Voice language: "+VoiceLanguages.NAMES[VoiceLanguages.selection(this)]+"  ▾",()->chooseVoiceLanguage());
        action("When: "+RelativeDelay.labelSeconds(quickDelaySeconds)+"  ▾",()->RelativeDelay.choose(this,true,quickDelaySeconds,selected->{quickDelaySeconds=selected;remember();render();}));
        action("Regular task · Daily / hourly  ▾",()->{remember();RoutineDialog.show(this,quickDraft,()->{quickDraft="";hideKeyboard();render();});});
        link("Add with time or repeat",()->{remember();if(!quickDraft.isEmpty()){draft=quickDraft;quickDraft="";quickEntry.setText("");}openEditor(false,0);});
        try(TaskStore store=new TaskStore(this)){List<TaskStore.Task> tasks=TaskSections.personal(store.activeAll());if(tasks.isEmpty())empty("A little space to breathe","Add your first task above.");else{section(tasks.size()+" to do");showTasks(tasks);}}
    }
    private void chooseVoiceLanguage(){
        new AlertDialog.Builder(this)
                .setTitle("Voice recognition language")
                .setSingleChoiceItems(VoiceLanguages.NAMES,VoiceLanguages.selection(this),(dialog,choice)->{
                    VoiceLanguages.select(this,choice);
                    dialog.dismiss();
                    remember();render();
                })
                .setNegativeButton("Cancel",null).show();
    }

    private void startVoiceTask(){
        remember();
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String language=VoiceLanguages.language(this);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,language);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,VoiceLanguages.prompt(language));
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        try { startActivityForResult(intent,REQUEST_VOICE_TASK); }
        catch(ActivityNotFoundException unavailable){
            VoiceTaskDialog.editCommand(this,quickDraft,VoiceLanguages.language(this),()->{quickDraft="";render();});
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_VOICE_TASK)return;
        if(result!=RESULT_OK||data==null)return;
        ArrayList<String> candidates=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(candidates==null||candidates.isEmpty()){
            VoiceTaskDialog.editCommand(this,quickDraft,VoiceLanguages.language(this),()->{quickDraft="";render();});
            return;
        }
        VoiceTaskDialog.editCommand(this,candidates.get(0),VoiceLanguages.language(this),()->{quickDraft="";render();});
    }

    private void quickAdd(){
        String title=quickEntry.getText().toString().trim();if(title.isEmpty()){quickEntry.setError("Enter a task name");return;}
        TaskStore.Task task;
        if(quickDelaySeconds==0) {
            try(TaskStore store=new TaskStore(this)){task=TaskSections.addPersonal(store,title);}
            TaskNotifications.show(this,task);
        } else {
            long target=System.currentTimeMillis()+quickDelaySeconds*1000L;
            try(TaskStore store=new TaskStore(this)){
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try{
                    task=store.add(title,1,target,TaskStore.REPEAT_NEVER,0);
                    task=store.setGroup(task.id,"");
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            ReminderScheduler.schedule(this,task);
        }
        if(!TaskNotifications.canNotify(this)){
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
            else toast("Enable PinTask notifications in Android settings to show new tasks.");
        }else if(quickDelaySeconds==0 && !TaskNotifications.canNotifyChannel(this,TaskNotifications.CHANNEL)){
            toast("Enable the Unfinished tasks notification category in Android settings.");
        }
        boolean delayed=quickDelaySeconds>0;
        quickDelaySeconds=0;
        TaskWidget.refreshAll(this);quickDraft="";hideKeyboard();render();
        if(delayed)ReminderHealth.warnWhenBlocked(this);
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
        link("Create custom shift",()->createCustomShift(true));
        link("How to set up shifts",this::tourStart);
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
        new AlertDialog.Builder(this).setTitle("Plan work days").setItems(new String[]{"Copy this day to other dates","Assign a shift to several dates"},(d,i)->{remember();copyingDay=i==0;planDates.clear();planTemplateId=0;screen=PLAN;render();}).setNegativeButton("Cancel",null).show();
    }
    private void planPage() {
        heading(copyingDay?"Copy day":"Plan several days",copyingDay?"Copy shifts and tasks from "+date.format(DateTimeFormatter.ofPattern("EEE, d MMM")):"Choose a shift and the dates you work it");
        link("‹ Back to Work",()->{screen=WORK;render();});
        String group=copyingDay?TaskGroups.selected(this):TaskGroups.newTaskGroup(this);
        text("Job / patient: "+(group==null?"All work":group.isEmpty()?"Unassigned":group),14);
        if(copyingDay)text("Copies the current job/patient selection into empty days. Completed tasks and checklists start fresh. Tasks with times already past are skipped.",14);
        else action("Shift: "+selectedCustomShiftLabel(),()->chooseCustomShift(true));
        action("Add date",()->{if(planDates.size()>=31){toast("Choose up to 31 dates at once");return;}LocalDate initial=planDates.isEmpty()?LocalDate.now():planDates.get(planDates.size()-1).plusDays(1);DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{LocalDate chosen=LocalDate.of(y,m+1,d);if(chosen.isBefore(LocalDate.now()) || (copyingDay && chosen.equals(date))){toast("Choose today or a future date different from the source day");return;}if(!planDates.contains(chosen))planDates.add(chosen);Collections.sort(planDates);render();},initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());picker.getDatePicker().setMinDate(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();});
        for(LocalDate chosen:planDates)link(chosen.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))+" · remove",()->{planDates.remove(chosen);render();});
        if(planDates.isEmpty())text("Add one or more dates above (up to 31).",14);
        primary((copyingDay?"Copy to ":"Apply to ")+planDates.size()+(planDates.size()==1?" day":" days"),()->applyPlan(false));
    }
    private void applyPlan(boolean overlap) {
        try {
            if(!copyingDay && planTemplateId==0)throw new IllegalArgumentException("Choose a shift first");
            if(copyingDay)repository.copyDay(date,planDates,overlap);
            else if(planTemplateId<0)repository.scheduleDates(planDates,"Off",overlap);
            else repository.scheduleCustomDates(planDates,planTemplateId,overlap);
            date=planDates.get(0);detailRun=0;screen=WORK;planDates.clear();TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);TaskWidget.refreshAll(this);render();toast("Days saved");
        } catch(IllegalArgumentException e) {
            if(!overlap && e.getMessage().startsWith("This overlaps"))new AlertDialog.Builder(this).setTitle("Shifts overlap").setMessage("Some selected dates overlap existing shifts. Keep both? All selected dates will be applied together.").setNegativeButton("Cancel",null).setPositiveButton("Keep both",(d,i)->applyPlan(true)).show();else toast(e.getMessage());
        }
    }
    private void pickWorkDate(){
        DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{date=LocalDate.of(y,m+1,d);render();chooseShift();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());
        picker.setTitle(tour.active()&&tour.stage()==ShiftGuide.DATE?"GUIDE · Choose when your shift happens":"Choose work day");
        picker.setOnCancelListener(d->tour.cancelled(ShiftGuide.DATE,ShiftGuide.SET_SHIFT));
        picker.show();
    }
    private void openEditor(boolean work,long run){
        remember();if(draft.isEmpty() || workEditor!=work)editorDate=work?date:LocalDate.now();if(run>0){ShiftRepository.Run chosen=repository.findRun(run);if(chosen!=null)editorDate=dateOf(chosen.startsAt);}if(workEditor!=work || (taskRun>0)!=(run>0))repeat=0;editorDelaySeconds=-1;workEditor=work;taskRun=run;editorGroup=work?TaskGroups.newTaskGroup(this):"";screen=EDITOR;render();
    }
    private void morePage(){
        heading("More","Extra tools, whenever you need them");
        section("ORGANIZE");tool("Task library & search","Find tasks across every group",()->startActivity(new Intent(this,MainActivity.class).putExtra("search_all",true)));
        tool("Completed tasks","Your finished task history",()->{screen=HISTORY;render();});tool("Task bundles","Reuse a set of tasks",()->TaskBundles.show(this,this::render));
        section("WORK SETUP");
        tool("Create custom shift","Choose your own name and working hours",()->createCustomShift(false));
        tool("Interactive shift guide","Follow highlighted buttons through a real shift setup",this::tourStart);
        tool("Saved shift templates","Optional templates and task rules",()->startActivity(new Intent(this,LegacyShiftActivity.class)));
        section("SETTINGS");
        tool("Permission checklist",StartupAccess.nextRequired(this)==StartupAccess.READY?
                "Core reminders allowed · Review startup permissions and battery":"Action needed · Grant missing reminder access",
                ()->startupAccess.review());
        tool("Reminder health check","Test the alarm sound, permissions and a 60-second scheduled alarm",()->ReminderHealth.show(this));
        tool("Reminder diagnostics","See scheduled, received and late alarms · Copy report",()->ReminderDiagnostics.show(this));
        tool("Reminder pop-up banners",TaskNotifications.popupEnabled(this)?
                "On · Show WhatsApp-style heads-up alerts for due tasks":"Off · Sound and status-bar icon without pop-up",this::popupSettings);
        tool("Watch notifications",TaskNotifications.watchEnabled(this)?"Phone + Wear OS · Done on your watch":"Phone only · pinned task cards",this::watchSettings);
        tool("Reminder timing",ReminderScheduler.canScheduleExact(this)?"Precise alarms enabled":"Approximate · Android may delay reminders",this::reminderTimingSettings);
        tool("Quick Add notification",TaskNotifications.quickAddEnabled(this)?"On · tap to change":"Off · optional notification shortcut",this::quickSettings);
        tool("Backup & restore","Export or restore your saved data",()->startActivity(new Intent(this,BackupActivity.class)));
        text("PinTask 3.7",12);
    }
    private void watchSettings(){new AlertDialog.Builder(this).setTitle("Watch notifications").setMessage("For a paired Wear OS watch, allow PinTask notifications in Galaxy Wearable or your watch companion app. Open a task notification on the watch and tap Done. The phone must be connected. Swiping a card does not complete the task; it stays in PinTask and may reappear when the app refreshes.")
        .setPositiveButton("Phone + watch",(d,i)->{TaskNotifications.setWatchEnabled(this,true);render();}).setNeutralButton("Phone only, pinned",(d,i)->{TaskNotifications.setWatchEnabled(this,false);render();}).setNegativeButton("Cancel",null).show();}
    private void popupSettings(){
        new AlertDialog.Builder(this)
                .setTitle("Reminder pop-up banners")
                .setMessage("On: due reminders use an urgent notification category that can pop up at the top of the screen. Off: reminders still sound and appear in the notification shade and status bar, but use a less interruptive category. ColorOS and Do Not Disturb can override these preferences.")
                .setSingleChoiceItems(new String[]{"Off · No heads-up banner","On · Show heads-up banner"},
                        TaskNotifications.popupEnabled(this)?1:0,(d,selected)->{
                            TaskNotifications.setPopupEnabled(this,selected==1);
                            d.dismiss();render();
                            if(!TaskNotifications.canNotifyChannel(this,TaskNotifications.activeReminderChannel(this)))
                                ReminderHealth.warnWhenBlocked(this);
                        })
                .setNegativeButton("Cancel",null).show();
    }
    private void quickSettings(){new AlertDialog.Builder(this).setTitle("Quick Add notification").setSingleChoiceItems(new String[]{"Off","On"},TaskNotifications.quickAddEnabled(this)?1:0,(d,index)->{TaskNotifications.setQuickAddEnabled(this,index==1);d.dismiss();render();if(index==1 && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);}).setNegativeButton("Cancel",null).show();}
    private void historyPage(){heading("Completed tasks","Your task history");link("‹ Back to More",()->navigate(MORE));try(TaskStore store=new TaskStore(this)){List<TaskStore.Task> done=store.completedAll();if(done.isEmpty())empty("Nothing completed yet","Finished tasks will appear here.");for(TaskStore.Task task:done)body.addView(TaskCards.done(this,task,this::render));}}
    private String selectedCustomShiftLabel() {
        if(planTemplateId==0)return "Choose custom shift";
        if(planTemplateId<0)return "Off · no shift";
        ShiftRepository.Template chosen=repository.findTemplate(planTemplateId);
        if(chosen==null)return "Choose custom shift";
        return chosen.name+" · "+clock(chosen.startMinute)+"–"+clock(chosen.endMinute());
    }
    private String clock(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}

    private void createCustomShift(boolean scheduleNow) {
        boolean guided=tour!=null&&tour.active()&&tour.stage()==ShiftGuide.FORM;
        CustomShiftDialog.show(this,repository,template->{
            if(guided) {
                tour.set(ShiftGuide.SET_SHIFT);
                render();
            } else if(scheduleNow)scheduleSelectedShift(template.id,false);
            else{render();toast("Custom shift saved. Choose it from Work → Set a shift.");}
        },()->{if(guided)tour.cancelled(ShiftGuide.FORM,ShiftGuide.CREATE);},guided);
    }

    private void chooseCustomShift(boolean planning) {
        List<ShiftRepository.Template> types=repository.templates();
        String[] labels=new String[types.size()+2];
        for(int i=0;i<types.size();i++){
            ShiftRepository.Template t=types.get(i);
            labels[i]=t.name+" · "+clock(t.startMinute)+"–"+clock(t.endMinute())+(t.endMinute()<=t.startMinute?" (+1 day)":"");
        }
        labels[types.size()]="＋ Create custom shift";
        labels[types.size()+1]="Off · no shift";
        new AlertDialog.Builder(this).setTitle(tour.active()&&tour.stage()==ShiftGuide.DATE?
                "GUIDE · Choose your saved custom shift":planning?"Select custom shift":"Schedule a shift · "+date.format(DateTimeFormatter.ofPattern("d MMM")))
            .setItems(labels,(d,index)->{
                if(index==types.size()){
                    CustomShiftDialog.show(this,repository,template->{
                        if(planning){planTemplateId=template.id;render();}
                        else scheduleSelectedShift(template.id,false);
                    });
                }else if(index==types.size()+1){
                    if(planning){planTemplateId=-1;render();}
                    else scheduleSelectedShift(-1,false);
                }else if(planning){planTemplateId=types.get(index).id;render();}
                else scheduleSelectedShift(types.get(index).id,false);
            }).setNegativeButton("Cancel",null)
            .setOnCancelListener(d->tour.cancelled(ShiftGuide.DATE,ShiftGuide.SET_SHIFT))
            .show();
    }

    private void scheduleSelectedShift(long templateId,boolean allowOverlap) {
        try {
            ShiftRepository.Run run=templateId<0?repository.schedule(date,"Off",allowOverlap):repository.scheduleCustom(date,templateId,allowOverlap);
            if(templateId>=0){detailRun=run.id;screen=WORK;
                if(tour.active()&&tour.stage()==ShiftGuide.DATE)tour.set(ShiftGuide.ADD_TASK);
            }
            TaskNotifications.syncAll(this);ReminderScheduler.rescheduleAll(this);render();
        }catch(IllegalArgumentException e){
            if(!allowOverlap && e.getMessage().startsWith("This overlaps"))
                new AlertDialog.Builder(this).setTitle("Shifts overlap")
                    .setMessage("This shift overlaps another one. Keep both? Existing tasks will not be deleted.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Keep both",(d,i)->scheduleSelectedShift(templateId,true)).show();
            else toast(e.getMessage());
        }
    }

    private void chooseShift() {chooseCustomShift(false);}
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
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                repository.saveRunAsTemplate(run.id,title.getText().toString());
                if(tour.active()&&tour.stage()==ShiftGuide.TEMPLATE_DIALOG)tour.done();
                dialog.dismiss();toast("Template saved");
            }catch(IllegalArgumentException e){title.setError(e.getMessage());}
        }));
        dialog.setOnDismissListener(d->tour.cancelled(ShiftGuide.TEMPLATE_DIALOG,ShiftGuide.TEMPLATE));
        dialog.show();
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
        name.setHint("Task name"); name.setText(draft);
        boolean guidedEntry=tour.active()&&tour.stage()==ShiftGuide.TYPING;
        name.setImeOptions(guidedEntry?android.view.inputmethod.EditorInfo.IME_ACTION_NEXT:android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        name.setOnEditorActionListener((v,action,event)->{
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT&&tour.active()&&tour.stage()==ShiftGuide.TYPING){
                nextGuidedTime();return true;
            }
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){saveTask();return true;}
            return false;
        }); name.setPadding(dp(14),dp(12),dp(14),dp(12));name.setBackground(shape(SURFACE,12));name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)}); body.addView(name);
        if(tour.active()&&tour.stage()==ShiftGuide.TYPING){
            text("GUIDE · Type your task name, then tap Next to highlight its time.",14);
            action("Next: Choose task time",this::nextGuidedTime);
        }
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
            TimePickerDialog picker=new TimePickerDialog(this,(v,h,m)->{
                // A chosen time is an audible reminder by default, never silently "No reminder".
                if(minute<0 && reminder==0)reminder=1;
                minute=h*60+m;editorDelaySeconds=-1;
                if(tour.active()&&tour.stage()==ShiftGuide.TIME_PICKER)tour.set(ShiftGuide.MORE);
                render();
            },initial/60,initial%60,true);
            picker.setOnCancelListener(d->tour.cancelled(ShiftGuide.TIME_PICKER,ShiftGuide.TIME));
            picker.show();
        });
        action("Or alert me: "+(editorDelaySeconds<0?"Choose a delay  ▾":RelativeDelay.labelSeconds(editorDelaySeconds)+"  ▾"),
                ()->RelativeDelay.choose(this,false,editorDelaySeconds,selected->{
                    remember();editorDelaySeconds=selected;minute=-1;
                    reminder=1;render();
                }));
        if(editorDelaySeconds>=0)text("The alarm counts from when you save this task."+
                (taskRun>0?" It must be inside the selected shift.":""),13);
        if(minute>=0 || editorDelaySeconds>=0)text("Alarm: "+(reminder==0?"Off (tap More options to enable)":reminder==1?"At task time":new String[]{"","At task time","5 minutes early","10 minutes early","15 minutes early","30 minutes early","1 hour early"}[reminder]),14);
        link(moreOptions?"Less options":"More options",()->{remember();moreOptions=!moreOptions;render();});
        main.addView(advanced);advanced.setVisibility(moreOptions?View.VISIBLE:View.GONE);body=advanced;
        if(run!=null && !dateOf(run.startsAt).equals(dateOf(run.endsAt)))text("After-midnight times belong to the next morning of this shift.",14);
        if(minute>=0 || editorDelaySeconds>=0) action("Remove time",()->{remember();minute=-1;editorDelaySeconds=-1;reminder=0;repeat=0;render();});
        text("Reminder · Optional",15);
        reminders=spinner(new String[]{"No reminder","At task time","5 minutes before","10 minutes before","15 minutes before","30 minutes before","1 hour before"},reminder);
        if(tour.active()){
            reminders.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int index,long id){
                    if(tour.active()&&tour.stage()==ShiftGuide.CHOOSING_REMINDER&&index>0){
                        reminder=index;tour.set(ShiftGuide.SAVE);
                        remember();render();
                    }
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            });
        }
        text("Repeat · Optional",15);
        if(taskRun==0) {
            repeats=new Spinner(this);RepeatRules.bind(this,repeats,repeat);body.addView(repeats);
            action("Selected weekdays…",()->{remember();RepeatRules.weekdays(this,repeat,code->{repeat=code;render();});});
            action("Custom repeat…",()->{remember();RepeatRules.custom(this,repeat,code->{repeat=code;render();});});
        } else repeats=spinner(new String[]{"Never","Every 30 minutes","Every hour","Every 2 hours","Every 3 hours","Every 4 hours"},repeat);
        body=main;
        primary("Save task",this::saveTask);
    }
    private void nextGuidedTime(){
        if(name==null||name.getText().toString().trim().isEmpty()){
            if(name!=null)name.setError("Enter a task name first");return;
        }
        remember();hideKeyboard();tour.set(ShiftGuide.TIME);render();
    }
    private void reminderTimingSettings() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Reminder timing")
                .setMessage("Allow alarms and reminders so timed tasks can arrive close to their chosen time, including while the phone is asleep. Without access, Android may delay them.")
                .setNegativeButton("Close", null);
        if (Build.VERSION.SDK_INT >= 31) dialog.setPositiveButton("Open settings", (d, which) -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (android.content.ActivityNotFoundException unavailable) {
                toast("Open Android settings, then Special app access → Alarms & reminders.");
            }
        });
        dialog.show();
    }

    private void saveTask() {
        remember();
        try {
            if(draft.trim().isEmpty()) { name.setError("Enter a task name"); return; }
            if(minute<0 && editorDelaySeconds<0 && (reminder>0 || repeat>0))
                throw new IllegalArgumentException("Choose a time or delay for reminders or repeats");
            if(taskRun>0) {
                if(editorDelaySeconds>=0)
                    repository.addTaskAfterSeconds(taskRun,draft,editorDelaySeconds,INTERVALS[repeat],EARLY[reminder]);
                else repository.addTask(taskRun,draft,minute,INTERVALS[repeat],EARLY[reminder]);
            } else {
                long due=editorDelaySeconds>=0?System.currentTimeMillis()+editorDelaySeconds*1000L:
                        minute<0?0:editorDate.atTime(minute/60,minute%60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if(due>0 && due<=System.currentTimeMillis()) throw new IllegalArgumentException("Choose a future time");
                try(TaskStore store=new TaskStore(this)) { android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{TaskStore.Task created=store.add(draft,1,due,repeat,EARLY[reminder]);store.setGroup(created.id,workEditor?(editorGroup.isEmpty()?"Work":editorGroup):"");db.setTransactionSuccessful();}finally{db.endTransaction();} }
            }
            if(workEditor && taskRun==0 && editorGroup.isEmpty() && "".equals(TaskGroups.selected(this)))TaskGroups.select(this,"Work");
            boolean wantsReminder=reminder>0;
            hideKeyboard();
            draft=""; minute=-1; editorDelaySeconds=-1; reminder=0; repeat=0; moreOptions=false;
            if(tour.active()&&tour.stage()==ShiftGuide.SAVING)tour.set(ShiftGuide.TEMPLATE);
            if(workEditor)detailRun=taskRun; screen=workEditor?1:0; render();
            TaskNotifications.syncAll(this); ReminderScheduler.rescheduleAll(this);
            if(wantsReminder)ReminderHealth.warnWhenBlocked(this);
            toast("Task saved");
        } catch(IllegalArgumentException e) {
            toast(e.getMessage());
            tour.cancelled(ShiftGuide.SAVING,ShiftGuide.SAVE);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        TaskNotifications.syncAll(this); ReminderScheduler.rescheduleAll(this);
        if(startupAccess!=null&&request==StartupAccess.REQUEST_NOTIFICATIONS){
            startupAccess.onPermissionResult(request);return;
        }
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
