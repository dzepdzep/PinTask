package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

/** First-open and replayable step-by-step help. Never changes or deletes tasks. */
final class ShiftGuide {
    private static final String PREF="custom_shifts_guide_seen_v28";
    static final String[] TITLES={
        "Welcome to PinTask",
        "1 · Create your shift",
        "2 · Schedule your shift",
        "3 · Add tasks inside it",
        "4 · Choose a time and reminder",
        "5 · Repeat and reuse",
        "6 · Done and Snooze"
    };
    static final String[] DETAILS={
        "PinTask keeps everyday tasks and work shifts separate. This short guide shows how to create your own shift hours and reminders.",
        "Open Work, tap Create custom shift, then enter a name and start/end times. For an overnight shift, enter 20:00 to 08:00. No preset shift is required.",
        "In Work, tap Set a shift, choose a date, then select your custom shift. Your shifts appear on that date. You can also plan several days from Work.",
        "Tap your scheduled shift to open it, then tap Add task to this shift. You can add several separate tasks for 10:00, 14:00 or any other time.",
        "Enter the task name, choose Time, then More options → Reminder. Select At task time or 5/10/15/30/60 minutes before; No reminder is silent.",
        "In More options, set a task to repeat every 30 minutes or 1–4 hours within its shift. To reuse an entire task schedule, tap Save as template inside that shift.",
        "Your task notification shows Done and Snooze together. Done finishes only that task. Snooze offers 5, 15, 30 minutes or a custom time. You can replay this guide from More → Shift setup guide."
    };

    static void maybeWelcome(Activity activity,Runnable openWork) {
        if(activity.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean(PREF,false))return;
        activity.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putBoolean(PREF,true).apply();
        activity.getWindow().getDecorView().postDelayed(()->{
            if(activity.isFinishing() || activity.isDestroyed())return;
            new AlertDialog.Builder(activity).setTitle("Welcome to PinTask")
                    .setMessage("Want a quick guide to creating your own work shifts and timed tasks?")
                    .setNegativeButton("Later",null)
                    .setPositiveButton("Show me",(d,i)->show(activity,0,openWork)).show();
        }, 550);
    }

    static void replay(Activity activity,Runnable openWork) {show(activity,0,openWork);}

    private static void show(Activity activity,int step,Runnable openWork) {
        AlertDialog.Builder builder=new AlertDialog.Builder(activity)
                .setTitle(TITLES[step]).setMessage(DETAILS[step])
                .setNegativeButton("Close",null);
        if(step>0)builder.setNeutralButton("Back",(d,i)->show(activity,step-1,openWork));
        if(step<TITLES.length-1)
            builder.setPositiveButton("Next",(d,i)->show(activity,step+1,openWork));
        else builder.setPositiveButton("Open Work",(d,i)->openWork.run());
        builder.show();
    }
}
