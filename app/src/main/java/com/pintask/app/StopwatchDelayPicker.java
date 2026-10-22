package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import java.util.regex.*;
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

    /** Typed input: plain numbers are minutes, HH:MM[:SS] is a duration.
     * Units can be combined: 1h 5m 20s, 90 seconds, 2 hours. */
    static int parseSeconds(String value) {
        String raw=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        if(raw.matches("\\d{1,3}:\\d{2}(?::\\d{2})?")) {
            String[] p=raw.split(":");
            int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);
            int s=p.length==3?Integer.parseInt(p[2]):0;
            return toSeconds(h,m,s);
        }
        if(raw.matches("\\d+"))return checkedSeconds((long)RelativeDelay.parse(raw)*60);
        Pattern segment=Pattern.compile("(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)");
        Matcher m=segment.matcher(raw);
        long seconds=0;
        int cursor=0;
        boolean any=false;
        while(m.find()){
            String skipped=raw.substring(cursor,m.start()).replaceAll("[\\s,]+","")
                    .replace("and","");
            if(!skipped.isEmpty())throw new IllegalArgumentException("Type e.g. 15m, 1:30:20 or 1h 30m.");
            long n=Long.parseLong(m.group(1));
            String unit=m.group(2);
            long multiplier=unit.startsWith("h")?3600:unit.startsWith("m")?60:1;
            if(n>MAX_SECONDS)throw new IllegalArgumentException("Maximum delay is 30 days");
            seconds+=n*multiplier;
            any=true;cursor=m.end();
        }
        if(!any||!raw.substring(cursor).trim().isEmpty()) {
            // Existing decimal formats remain supported in the app's relative picker.
            try{return checkedSeconds((long)RelativeDelay.parse(raw)*60);}
            catch(IllegalArgumentException ignored){
                throw new IllegalArgumentException("Type e.g. 15m, 1:30:20, 90s or 1h 30m.");
            }
        }
        return checkedSeconds(seconds);
    }

    private static int checkedSeconds(long seconds){
        if(seconds<1||seconds>MAX_SECONDS)
            throw new IllegalArgumentException("Select a duration from 1 second to 30 days.");
        return (int)seconds;
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
        show(activity,selectedSeconds,false,saveSeconds);
    }

    static void show(Activity activity,int selectedSeconds,boolean allowNow,IntConsumer saveSeconds) {
        int initial=selectedSeconds>0?Math.min(selectedSeconds,MAX_SECONDS):15*60;
        int[] h={initial/3600},m={(initial%3600)/60},s={initial%60};
        NumberPicker[] pickers=new NumberPicker[3];

        LinearLayout content=new LinearLayout(activity);
        content.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,4));
        content.setOrientation(LinearLayout.VERTICAL);
        TextView guidance=label(activity,"Scroll the wheels to choose a duration",14);
        guidance.setPadding(0,0,0,dp(activity,8));
        content.addView(guidance);

        RadioGroup method=new RadioGroup(activity);
        method.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton wheelsMode=new RadioButton(activity);
        wheelsMode.setText("Scroll wheels");
        wheelsMode.setId(View.generateViewId());
        RadioButton typeMode=new RadioButton(activity);
        typeMode.setText("Type duration");
        typeMode.setId(View.generateViewId());
        method.addView(wheelsMode,new RadioGroup.LayoutParams(0,dp(activity,48),1));
        method.addView(typeMode,new RadioGroup.LayoutParams(0,dp(activity,48),1));
        content.addView(method);

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
        EditText typed=new EditText(activity);
        typed.setSingleLine(true);
        typed.setContentDescription("Type stopwatch duration");
        typed.setHint("15m, 1:30:20 or 1h 30m");
        typed.setInputType(InputType.TYPE_CLASS_TEXT);
        typed.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        typed.setText(display(initial));
        typed.setVisibility(View.GONE);
        content.addView(typed);
        android.widget.Button presets=new android.widget.Button(activity);
        presets.setText("Quick presets: 5, 10, 15, 30 or 60 min");
        presets.setAllCaps(false);
        presets.setContentDescription("Quick delay presets");
        presets.setOnClickListener(v->new AlertDialog.Builder(activity)
                .setTitle("Quick delay")
                .setItems(new String[]{"5 minutes","10 minutes","15 minutes","30 minutes","1 hour"},
                        (d,which)->{
                            int value=new int[]{5,10,15,30,60}[which]*60;
                            pickers[0].setValue(value/3600);
                            pickers[1].setValue(value%3600/60);
                            pickers[2].setValue(0);
                            method.check(wheelsMode.getId());
                        }).setNegativeButton("Cancel",null).show());
        content.addView(presets);
        method.setOnCheckedChangeListener((group,id)->{
            boolean type=id==typeMode.getId();
            wheels.setVisibility(type?View.GONE:View.VISIBLE);
            typed.setVisibility(type?View.VISIBLE:View.GONE);
            if(type){
                int current=pickers[0].getValue()*3600+
                        pickers[1].getValue()*60+pickers[2].getValue();
                typed.setText(display(current));
                typed.setSelection(typed.length());
            }
        });
        method.check(wheelsMode.getId());

        TextView preview=label(activity,display(initial),29);
        preview.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        preview.setContentDescription("Selected delay");
        preview.setPadding(0,dp(activity,10),0,dp(activity,8));
        content.addView(preview);
        typed.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(!typeMode.isChecked())return;
                try{preview.setText(display(parseSeconds(s.toString())));}
                catch(IllegalArgumentException ignored){preview.setText("Enter a valid duration");}
            }
            @Override public void afterTextChanged(android.text.Editable value){}
        });
        TextView hint=label(activity,"The countdown starts when you save the task.",12);
        content.addView(hint);

        AlertDialog.Builder builder=new AlertDialog.Builder(activity)
                .setTitle("Choose alert delay · Stopwatch")
                .setView(content)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Set delay",null);
        if(allowNow)builder.setNeutralButton("Now",(d,w)->saveSeconds.accept(0));
        AlertDialog dialog=builder.create();
        for(NumberPicker picker:pickers){
            picker.setOnValueChangedListener((view,oldValue,newValue)->{
                int total=pickers[0].getValue()*3600+pickers[1].getValue()*60+pickers[2].getValue();
                boolean valid=total>0&&total<=MAX_SECONDS;
                preview.setText(valid?display(total):total==0?"Choose a duration":"Maximum 30 days");
                android.widget.Button confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if(confirm!=null)confirm.setEnabled(valid);
            });
        }
        android.view.inputmethod.InputMethodManager keyboard=
                activity.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int duration=typeMode.isChecked()?
                        parseSeconds(typed.getText().toString()):
                        toSeconds(pickers[0].getValue(),pickers[1].getValue(),pickers[2].getValue());
                saveSeconds.accept(duration);
                dialog.dismiss();
            }catch(IllegalArgumentException wrong){
                if(typeMode.isChecked())typed.setError(wrong.getMessage());
                android.widget.Toast.makeText(activity,wrong.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        });
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
