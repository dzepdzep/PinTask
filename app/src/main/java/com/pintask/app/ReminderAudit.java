package com.pintask.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import java.util.Date;

/** Device-local audit without task text or personal information. */
final class ReminderAudit {
    private static final String FILE="reminder_diagnostics_v36";
    private ReminderAudit(){}
    static void note(Context context,String event,long id,long expected) {
        long now=System.currentTimeMillis();
        SharedPreferences p=context.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        String line=DateFormat.format("MM-dd HH:mm:ss",new Date(now))+
                "  "+event+" #"+id+
                (expected>0?" due "+DateFormat.format("MM-dd HH:mm:ss",new Date(expected)):"");
        String history=p.getString("events","");
        String[] previous=history.isEmpty()?new String[0]:history.split("\n");
        StringBuilder updated=new StringBuilder(line);
        for(int i=0;i<Math.min(29,previous.length);i++)updated.append("\n").append(previous[i]);
        SharedPreferences.Editor edit=p.edit().putString("events",updated.toString());
        if(event.equals("alarm_callback"))edit.putLong("last_alarm",now);
        if(event.equals("watchdog_check"))edit.putLong("last_watchdog",now);
        if(event.equals("delivered") || event.startsWith("delivered_"))edit.putLong("last_delivered",now);
        if(event.startsWith("recovered_by_") || event.equals("overdue_recovered"))edit.putLong("last_recovered",now);
        // Scheduling can run over many task rows after a reboot. Keep its audit
        // asynchronous rather than issuing an fsync for each alarm in the loop.
        if(event.startsWith("scheduled_") || event.equals("cancelled"))edit.apply();
        else edit.commit();
    }
    static String summary(Context context) {
        SharedPreferences p=context.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        return "Last alarm callback: "+when(p.getLong("last_alarm",0))+
                "\nLast independent background check: "+when(p.getLong("last_watchdog",0))+
                "\nLast posted reminder: "+when(p.getLong("last_delivered",0))+
                "\nLast recovery by a non-alarm path: "+when(p.getLong("last_recovered",0))+
                "\n\nRecent events (newest first):\n"+
                p.getString("events","No reminder events recorded yet.")+
                "\n\nThese entries log task IDs and times only, not task names. "+
                "Event suffixes distinguish AlarmManager callbacks, background watchdog checks, " +
                "and alarms delivered when the app reopens. Background checks can be delayed by Android and ColorOS.";
    }
    private static String when(long at){
        return at==0?"Never recorded":DateFormat.format("dd MMM yyyy HH:mm:ss",new Date(at)).toString();
    }
}
