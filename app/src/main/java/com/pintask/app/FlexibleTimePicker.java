package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * A shared two-mode picker for task clock times and early-reminder durations.
 * Wheel mode is explicitly separate from typed input; never silently guess.
 * Values are returned only after the user taps Use time / Set reminder.
 */
final class FlexibleTimePicker {
    private FlexibleTimePicker() {}
    static final int MAX_EARLY_MINUTES=1440;

    /** 24-hour clock, strictly HH:mm or H:mm; 24:00 is not a local clock time. */
    static int parseClock(String value) {
        String raw=value==null?"":value.trim();
        if(!raw.matches("\\d{1,2}:\\d{2}"))
            throw new IllegalArgumentException("Type a 24-hour time, such as 03:30 or 15:45.");
        String[] parts=raw.split(":");
        int h=Integer.parseInt(parts[0]),m=Integer.parseInt(parts[1]);
        if(h>23||m>59)
            throw new IllegalArgumentException("Use hours 00–23 and minutes 00–59.");
        return h*60+m;
    }

    /** 0=at due time, 1..1440 minutes early, supporting 75, 1:15 and 1h 15m. */
    static int parseAdvance(String value) {
        String raw=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        final int total;
        if(raw.equals("0")||raw.equals("0:00"))return 0;
        if(raw.matches("\\d{1,2}:\\d{2}")) {
            String[] fields=raw.split(":");
            int hours=Integer.parseInt(fields[0]),minute=Integer.parseInt(fields[1]);
            if(minute>59)throw new IllegalArgumentException("Minutes must be between 00 and 59.");
            total=hours*60+minute;
        } else {
            total=RelativeDelay.parse(raw);
        }
        return checkedAdvance(total);
    }

    static int checkedAdvance(int value) {
        if(value<0||value>MAX_EARLY_MINUTES)
            throw new IllegalArgumentException("Choose from 0 minutes to 24 hours before the task.");
        return value;
    }

    static String advanceLabel(int value) {
        if(value<=0)return "At task time";
        int h=value/60,m=value%60;
        if(h==0)return value+" minute"+(value==1?"":"s")+" before";
        if(m==0)return h+" hour"+(h==1?"":"s")+" before";
        return h+"h "+String.format(Locale.ROOT,"%02dm",m)+" before";
    }

    static void clock(Activity activity,int selectedMinute,IntConsumer chosen) {
        show(activity,false,Math.max(0,Math.min(selectedMinute,1439)),chosen);
    }

    static void advance(Activity activity,int selectedMinutes,IntConsumer chosen) {
        show(activity,true,checkedAdvance(selectedMinutes),chosen);
    }

    private static int dp(Activity a,int size) {
        return Math.round(a.getResources().getDisplayMetrics().density*size);
    }

    private static NumberPicker wheel(Activity a,int max,String description,int value) {
        NumberPicker picker=new NumberPicker(a);
        picker.setMinValue(0);picker.setMaxValue(max);
        picker.setWrapSelectorWheel(max==59);
        picker.setFormatter(n->String.format(Locale.ROOT,"%02d",n));
        picker.setValue(value);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        picker.setContentDescription(description);
        return picker;
    }

    private static TextView label(Activity a,String text) {
        TextView view=new TextView(a);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
        return view;
    }

    private static void show(Activity activity,boolean early,int initial,IntConsumer chosen) {
        int pad=dp(activity,16);
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad,dp(activity,8),pad,pad);

        TextView explain=label(activity,early?
                "Set exactly how long before the task to alert you. 0 means at task time.":
                "Set the task time in 24-hour format.");
        explain.setPadding(0,0,0,dp(activity,9));
        panel.addView(explain);

        RadioGroup method=new RadioGroup(activity);
        method.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton wheelsMode=new RadioButton(activity);
        wheelsMode.setText("Scroll wheels");
        wheelsMode.setId(View.generateViewId());
        RadioButton typedMode=new RadioButton(activity);
        typedMode.setText("Type");
        typedMode.setId(View.generateViewId());
        method.addView(wheelsMode,new RadioGroup.LayoutParams(0,dp(activity,48),1));
        method.addView(typedMode,new RadioGroup.LayoutParams(0,dp(activity,48),1));
        panel.addView(method);

        LinearLayout wheels=new LinearLayout(activity);
        wheels.setGravity(Gravity.CENTER);
        NumberPicker hours=wheel(activity,early?24:23,early?
                "Reminder hours before":"Clock hour",initial/60);
        NumberPicker minutes=wheel(activity,59,early?
                "Reminder minutes before":"Clock minute",initial%60);
        LinearLayout[] cells={new LinearLayout(activity),new LinearLayout(activity)};
        String[] titles={"HOURS","MINUTES"};
        NumberPicker[] pickers={hours,minutes};
        for(int i=0;i<2;i++){
            LinearLayout cell=cells[i];
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView title=label(activity,titles[i]);
            title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            cell.addView(title);
            cell.addView(pickers[i],new LinearLayout.LayoutParams(-1,dp(activity,144)));
            wheels.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        panel.addView(wheels);

        EditText input=new EditText(activity);
        input.setSingleLine(true);
        input.setHint(early?"75, 1:15 or 1h 15m":"HH:mm (e.g. 03:30)");
        input.setContentDescription(early?"Type minutes or hours before task":"Type 24-hour clock time");
        input.setInputType(early?InputType.TYPE_CLASS_TEXT:
                InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        input.setText(early?Integer.toString(initial):
                String.format(Locale.ROOT,"%02d:%02d",initial/60,initial%60));
        panel.addView(input);
        input.setVisibility(View.GONE);

        TextView preview=label(activity,"");
        preview.setTextSize(23);
        preview.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        preview.setContentDescription(early?"Advance reminder preview":"Task clock preview");
        preview.setPadding(0,dp(activity,10),0,dp(activity,8));
        panel.addView(preview);
        Runnable refresh=()->{
            int h=hours.getValue(),m=minutes.getValue();
            preview.setText(early?(h==24&&m!=0?"Maximum 24h":
                    advanceLabel(h*60+m)):String.format(Locale.ROOT,"%02d:%02d",h,m));
        };
        hours.setOnValueChangedListener((v,oldValue,newValue)->refresh.run());
        minutes.setOnValueChangedListener((v,oldValue,newValue)->refresh.run());
        refresh.run();

        method.setOnCheckedChangeListener((group,id)->{
            boolean typed=id==typedMode.getId();
            wheels.setVisibility(typed?View.GONE:View.VISIBLE);
            input.setVisibility(typed?View.VISIBLE:View.GONE);
            if(typed){
                int value=hours.getValue()*60+minutes.getValue();
                input.setText(early?Integer.toString(value):
                        String.format(Locale.ROOT,"%02d:%02d",hours.getValue(),minutes.getValue()));
                input.setSelection(input.length());
            }
        });
        method.check(wheelsMode.getId());
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle(early?"Remind me earlier":"Choose task time")
                .setView(panel)
                .setNegativeButton("Cancel",null)
                .setPositiveButton(early?"Set reminder":"Use time",null).create();
        dialog.show();
        // Install the confirm handler synchronously. Some UI test environments
        // defer OnShow callbacks, which would otherwise allow a no-op tap.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int value=typedMode.isChecked()?(early?parseAdvance(input.getText().toString()):
                        parseClock(input.getText().toString())):
                        hours.getValue()*60+minutes.getValue();
                if(early)checkedAdvance(value);
                chosen.accept(value);
                dialog.dismiss();
            }catch(IllegalArgumentException failure){
                if(typedMode.isChecked())input.setError(failure.getMessage());
                Toast.makeText(activity,failure.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
