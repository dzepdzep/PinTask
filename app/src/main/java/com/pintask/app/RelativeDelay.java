package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quick relative-time choices; durations are anchored at the moment a task is saved. */
final class RelativeDelay {
    private RelativeDelay() {}
    static final String[] OPTIONS={"Now","In 5 minutes","In 10 minutes","In 15 minutes",
            "In 30 minutes","In 1 hour","Custom delay…"};
    static final int[] MINUTES={0,5,10,15,30,60};
    private static final int MAX_MINUTES=30*24*60;
    private static final Pattern PART=Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m)",Pattern.CASE_INSENSITIVE);

    static String label(int minutes){
        if(minutes==0)return "Now";
        if(minutes%60==0)return "In "+(minutes/60)+" "+(minutes==60?"hour":"hours");
        return "In "+minutes+" minutes";
    }

    /** Accepts 90, 90 minutes, 2 hours, 1h 30m, 1.5 hours. No clock time input. */
    static int parse(String input) {
        String raw=input==null?"":input.trim().toLowerCase(Locale.ROOT);
        if(raw.matches("\\d+"))return checked(Double.parseDouble(raw));
        Matcher m=PART.matcher(raw);
        double total=0;
        int cursor=0;boolean found=false;
        while(m.find()) {
            String skipped=raw.substring(cursor,m.start()).replaceAll("[\\s,]+","").replace("and","");
            if(!skipped.isEmpty())throw new IllegalArgumentException("Use e.g. 90 minutes or 2 hours");
            double number=Double.parseDouble(m.group(1).replace(',','.'));
            String unit=m.group(2);
            total+=number*(unit.startsWith("h")?60:1);
            found=true;cursor=m.end();
        }
        if(!found||!raw.substring(cursor).trim().isEmpty())
            throw new IllegalArgumentException("Use e.g. 90 minutes or 2 hours");
        return checked(total);
    }
    private static int checked(double n) {
        if(!Double.isFinite(n)||n<1||n>MAX_MINUTES||n!=Math.rint(n))
            throw new IllegalArgumentException("Choose 1 minute to 30 days, in whole minutes");
        return (int)n;
    }

    /** Human readable label for delays accurate down to the second. */
    static String labelSeconds(int seconds) {
        if(seconds==0)return "Now";
        if(seconds%60==0)return label(seconds/60);
        int hours=seconds/3600, minutes=(seconds%3600)/60, remainder=seconds%60;
        StringBuilder text=new StringBuilder("In ");
        if(hours>0)text.append(hours).append("h ");
        if(minutes>0)text.append(minutes).append("m ");
        text.append(remainder).append("s");
        return text.toString();
    }

    /** Presets remain one tap. Custom opens scrollable stopwatch-style wheels. */
    static void choose(Activity activity,boolean allowNow,int currentlySelectedSeconds,
            IntConsumer pickedSeconds) {
        int shift=allowNow?0:1;
        String[] labels=new String[OPTIONS.length-shift];
        System.arraycopy(OPTIONS,shift,labels,0,labels.length);
        new AlertDialog.Builder(activity).setTitle("When should this task alert?")
                .setItems(labels,(d,index)->{
                    int original=index+shift;
                    if(original<MINUTES.length) pickedSeconds.accept(MINUTES[original]*60);
                    else StopwatchDelayPicker.show(activity,currentlySelectedSeconds,pickedSeconds);
                }).setNegativeButton("Cancel",null).show();
    }
}
