package com.pintask.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** A spotlight coach that points to real screens and forwards taps to real controls. */
final class ShiftGuide {
    static final int WORK=0,CREATE=1,FORM=2,SET_SHIFT=3,DATE=4,
        ADD_TASK=5,NAME=6,TYPING=7,TIME=8,TIME_PICKER=9,
        MORE=10,REMINDER=11,CHOOSING_REMINDER=12,SAVE=13,
        SAVING=14,TEMPLATE=15,TEMPLATE_DIALOG=16;
    private static final String PREF="interactive_shift_guide_v29";
    static final String[] TITLES={"Open Work","Create your custom shift","Schedule your shift",
        "Add a shift task","Type your task name","Choose its time","More options",
        "Set a reminder","Save the task","Reuse the shift"};
    static final String[] DETAILS={"Tap Work in the bottom bar.",
        "Tap Create custom shift, fill in your own name and hours, then save.",
        "Tap Set a shift. Pick a date and then choose your saved shift.",
        "Inside your shift, tap Add task to this shift.",
        "Tap the task name field, type your task and select Next.",
        "Set the time this task should be done.",
        "Open More options to choose how the reminder behaves.",
        "Select At task time, or a reminder before the task.",
        "Save the real task. You can come back and add more.",
        "Optional: Save as template to reuse these tasks in future shifts."};
    private final ShiftActivity a;
    private FrameLayout shade;
    private int stage=WORK;
    private boolean running;
    ShiftGuide(ShiftActivity activity){a=activity;}
    boolean active(){return running;}
    int stage(){return stage;}
    void set(int s){stage=s;}
    void restore(boolean on,int s){running=on;stage=s;}
    void start(){finishOverlay();running=true;stage=WORK;a.tourOpenTasks();}
    void stop(){running=false;finishOverlay();}
    void done(){stop();Toast.makeText(a,"Guide finished · Replay from More whenever you want.",Toast.LENGTH_LONG).show();}
    void cancelled(int waiting,int again){
        if(running&&stage==waiting){stage=again;a.tourRefresh();}
    }
    static void maybeWelcome(ShiftActivity a){
        if(a.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean(PREF,false))return;
        a.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putBoolean(PREF,true)
            .putBoolean("custom_shifts_guide_seen_v28",true).apply();
        a.getWindow().getDecorView().postDelayed(()->{
            if(a.isFinishing()||a.isDestroyed())return;
            new AlertDialog.Builder(a).setTitle("Try an interactive PinTask tour")
                .setMessage("Want the app to highlight the real buttons while you create a shift and its first timed task?")
                .setNegativeButton("Later",null)
                .setPositiveButton("Start guide",(d,w)->a.tourStart()).show();
        },550);
    }
    void onRender(View root){
        finishOverlay();
        if(!running||stage==FORM||stage==DATE||stage==TYPING||
            stage==TIME_PICKER||stage==CHOOSING_REMINDER||stage==SAVING||stage==TEMPLATE_DIALOG)return;
        root.post(()->{
            if(!running||shade!=null||a.isFinishing()||a.isDestroyed())return;
            View target=target(root);if(target==null)return;
            target.requestRectangleOnScreen(new Rect(0,0,Math.max(target.getWidth(),1),Math.max(target.getHeight(),1)),true);
            target.postDelayed(()->{if(running&&shade==null&&target.isAttachedToWindow())draw(target);},100);
        });
    }
    private View target(View root){
        switch(stage){
            case WORK:return find(root,"Work",false);
            case CREATE:return find(root,"Create custom shift",false);
            case SET_SHIFT:return find(root,"Set a shift",false);
            case ADD_TASK:return find(root,"Add task to this shift",false);
            case NAME:return a.tourTitleField();
            case TIME:return find(root,"Time · ",true);
            case MORE:return find(root,"More options",false);
            case REMINDER:return a.tourReminderSelector();
            case SAVE:return find(root,"Save task",false);
            case TEMPLATE:return find(root,"Save as template",false);
            default:return null;
        }
    }
    private View find(View root,String text,boolean prefix){
        if(root instanceof TextView){
            String value=((TextView)root).getText().toString();
            if(prefix?value.startsWith(text):value.equals(text))return root;
        }
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View found=find(((ViewGroup)root).getChildAt(i),text,prefix);
            if(found!=null)return found;
        }
        return null;
    }
    private int dp(int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private void draw(View target){
        FrameLayout decor=(FrameLayout)a.getWindow().getDecorView();
        int[] p=new int[2],q=new int[2];decor.getLocationOnScreen(p);target.getLocationOnScreen(q);
        RectF focus=new RectF(q[0]-p[0]-dp(5),q[1]-p[1]-dp(5),
            q[0]-p[0]+target.getWidth()+dp(5),q[1]-p[1]+target.getHeight()+dp(5));
        if(focus.width()<=0||focus.height()<=0)return;
        shade=new FrameLayout(a);decor.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View scrim=new View(a){
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xC9000716);
                c.drawRect(0,0,getWidth(),getHeight(),paint);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawRoundRect(focus,dp(12),dp(12),paint);paint.setXfermode(null);
                c.restoreToCount(layer);
                paint.setColor(0xFFA6E9CD);paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));c.drawRoundRect(focus,dp(12),dp(12),paint);
                paint.setStyle(Paint.Style.FILL);
            }
            @Override public boolean onTouchEvent(MotionEvent ev){
                if(ev.getAction()==MotionEvent.ACTION_UP&&focus.contains(ev.getX(),ev.getY()))tap(target);
                return true;
            }
        };
        scrim.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        shade.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout coach=new LinearLayout(a);coach.setOrientation(LinearLayout.VERTICAL);
        coach.setPadding(dp(16),dp(12),dp(16),dp(12));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(0xFF182233);background.setCornerRadius(dp(16));background.setStroke(dp(1),0xFFA6E9CD);
        coach.setBackground(background);
        int idx=stepNumber();
        coach.addView(label("STEP "+idx+" / 10",12,0xFFA6E9CD,true));
        coach.addView(label(TITLES[idx-1],18,Color.WHITE,true));
        coach.addView(label(DETAILS[idx-1],14,0xFFE5EAF1,false));
        LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView exit=label("Exit",13,Color.LTGRAY,false);
        exit.setPadding(dp(12),dp(12),dp(12),dp(12));exit.setOnClickListener(v->stop());actions.addView(exit);
        Button use=new Button(a);use.setAllCaps(false);use.setText("Tap highlighted control");
        use.setTextSize(11);use.setTextColor(0xFF102033);
        use.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFA6E9CD));
        use.setOnClickListener(v->tap(target));actions.addView(use);coach.addView(actions);
        if(stage==TEMPLATE){
            TextView skip=label("Finish guide without saving a template",13,0xFFA6E9CD,false);
            skip.setGravity(Gravity.CENTER);skip.setOnClickListener(v->done());coach.addView(skip);
        }
        FrameLayout.LayoutParams loc=new FrameLayout.LayoutParams(-1,-2);
        loc.leftMargin=dp(14);loc.rightMargin=dp(14);
        int estimated=dp(stage==TEMPLATE?230:185),bottom=(int)focus.bottom+dp(14);
        loc.topMargin=bottom+estimated<decor.getHeight()-dp(16)?bottom:
            Math.max(dp(15),(int)focus.top-estimated-dp(14));
        shade.addView(coach,loc);
    }
    private int stepNumber(){
        switch(stage){
            case WORK:return 1;case CREATE:return 2;case SET_SHIFT:return 3;
            case ADD_TASK:return 4;case NAME:return 5;case TIME:return 6;
            case MORE:return 7;case REMINDER:return 8;case SAVE:return 9;
            case TEMPLATE:return 10;default:return 1;
        }
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(dp(2),dp(3),dp(2),dp(5));
        if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private void tap(View real){
        int previous=stage;finishOverlay();
        switch(previous){
            case WORK:stage=CREATE;break;case CREATE:stage=FORM;break;
            case SET_SHIFT:stage=DATE;break;case ADD_TASK:stage=NAME;break;
            case NAME:stage=TYPING;break;case TIME:stage=TIME_PICKER;break;
            case MORE:stage=REMINDER;break;case REMINDER:stage=CHOOSING_REMINDER;break;
            case SAVE:stage=SAVING;break;case TEMPLATE:stage=TEMPLATE_DIALOG;break;
        }
        if(previous==NAME){
            real.requestFocus();
            android.view.inputmethod.InputMethodManager input=a.getSystemService(android.view.inputmethod.InputMethodManager.class);
            if(input!=null)input.showSoftInput(real,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }else real.performClick();
    }
    private void finishOverlay(){
        if(shade!=null){
            if(shade.getParent() instanceof ViewGroup)((ViewGroup)shade.getParent()).removeView(shade);
            shade=null;
        }
    }
}
