package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Stopwatch-style HH:MM:SS delay wheels. These select a duration, never a clock
 * time; seconds are retained when scheduling the actual AlarmManager reminder.
 */
final class StopwatchDelayPicker {
    private static final int MAX_SECONDS=30*24*60*60;
    private StopwatchDelayPicker(){}

    static int toSeconds(int hours,int minutes,int seconds) {
        if(hours<0||hours>720||minutes<0||minutes>59||seconds<0||seconds>59)
            throw new IllegalArgumentException("Choose valid hours, minutes and seconds");
        long sum=(long)hours*3600+minutes*60+seconds;
        if(sum<1||sum>MAX_SECONDS)
            throw new IllegalArgumentException("Select a duration from 1 second to 30 days");
        return (int)sum;
    }

    private static int dp(Activity activity,int value) {
        return Math.round(value*activity.getResources().getDisplayMetrics().density);
    }

    private static NumberPicker wheel(Activity activity,int max,String accessibility) {
        NumberPicker picker=new NumberPicker(activity);
        picker.setMinValue(0);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(max==59);
        picker.setFormatter(v->String.format(Locale.ROOT,"%02d",v));
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        picker.setContentDescription(accessibility);
        picker.setMinimumHeight(dp(activity,136));
        return picker;
    }

    private static TextView label(Activity activity,String text,int sp) {
        TextView view=new TextView(activity);
        view.setText(text);view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);return view;
    }

    private static String display(int totalSeconds) {
        int hours=totalSeconds/3600, minutes=totalSeconds%3600/60,seconds=totalSeconds%60;
        return String.format(Locale.ROOT,"%02d:%02d:%02d",hours,minutes,seconds);
    }

    static void show(Activity activity,int selectedSeconds,IntConsumer saveSeconds) {
        int initial=selectedSeconds>0?Math.min(selectedSeconds,MAX_SECONDS):15*60;
        int[] h={initial/3600},m={(initial%3600)/60},s={initial%60};
        NumberPicker[] pickers=new NumberPicker[3];

        LinearLayout content=new LinearLayout(activity);
        content.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,4));
        content.setOrientation(LinearLayout.VERTICAL);
        TextView guidance=label(activity,"Scroll the wheels to choose a duration",14);
        guidance.setPadding(0,0,0,dp(activity,8));
        content.addView(guidance);

        LinearLayout wheels=new LinearLayout(activity);
        wheels.setGravity(Gravity.CENTER);
        for(int i=0;i<3;i++){
            final int column=i;
            LinearLayout cell=new LinearLayout(activity);
            cell.setGravity(Gravity.CENTER);
            cell.setOrientation(LinearLayout.VERTICAL);
            TextView title=label(activity,new String[]{"HOURS","MINUTES","SECONDS"}[i],11);
            title.setTypeface(null,Typeface.BOLD);
            cell.addView(title);
            NumberPicker picker=wheel(activity,i==0?720:59,
                    new String[]{"Delay hours","Delay minutes","Delay seconds"}[i]);
            picker.setValue(i==0?h[0]:i==1?m[0]:s[0]);
            pickers[i]=picker;
            cell.addView(picker,new LinearLayout.LayoutParams(-1,dp(activity,145)));
            cell.setTag("delay_wheel_"+column);
            wheels.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        content.addView(wheels);

        TextView preview=label(activity,display(initial),29);
        preview.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        preview.setContentDescription("Selected delay");
        preview.setPadding(0,dp(activity,10),0,dp(activity,8));
        content.addView(preview);
        TextView hint=label(activity,"The countdown starts when you save the task.",12);
        content.addView(hint);

        for(int i=0;i<3;i++){
            LinearLayout cell=(LinearLayout)wheels.getChildAt(i);
            NumberPicker picker=(NumberPicker)cell.getChildAt(1);
            final int index=i;
            picker.setOnValueChangedListener((view,oldValue,newValue)->{
                if(index==0)h[0]=newValue;
                else if(index==1)m[0]=newValue;
                else s[0]=newValue;
                long total=(long)h[0]*3600+m[0]*60+s[0];
                preview.setText(total>MAX_SECONDS?"Maximum 30 days":display((int)total));
            });
        }

        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Custom delay · Stopwatch")
                .setView(content)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Set delay",null).create();
        android.view.inputmethod.InputMethodManager keyboard=
                activity.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0);
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int duration=toSeconds(pickers[0].getValue(),pickers[1].getValue(),pickers[2].getValue());
                dialog.dismiss();
                saveSeconds.accept(duration);
            }catch(IllegalArgumentException wrong){
                preview.setText(wrong.getMessage());
            }
        }));
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
