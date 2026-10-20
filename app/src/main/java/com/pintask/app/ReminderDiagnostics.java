package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;

/** Visible evidence of scheduled/received/recovered alerts without task names. */
final class ReminderDiagnostics {
    private ReminderDiagnostics(){}
    static void show(Activity activity) {
        String report=ReminderAudit.summary(activity)+"\n\n"+StartupAccess.summary(activity);
        new AlertDialog.Builder(activity)
                .setTitle("Reminder diagnostics")
                .setMessage(report)
                .setPositiveButton("Copy report",(d,w)->{
                    ClipboardManager clipboard=activity.getSystemService(ClipboardManager.class);
                    if(clipboard!=null){
                        clipboard.setPrimaryClip(ClipData.newPlainText("PinTask reminder diagnostics",report));
                        Toast.makeText(activity,"Reminder report copied",Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Check overdue now",(d,w)->{
                    ReminderWatchdog.check(activity);
                    show(activity);
                })
                .setNegativeButton("Close",null)
                .show();
    }
}
