package com.pintask.app;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Handles notification actions even when the main screen is not open. */
public final class TaskActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (TaskNotifications.ACTION_QUICK_ADD.equals(action)) {
            if(!TaskNotifications.quickAddEnabled(context)) return;
            Bundle answers = RemoteInput.getResultsFromIntent(intent);
            if (answers == null) return;
            CharSequence typed = answers.getCharSequence(TaskNotifications.REMOTE_TEXT);
            String title = typed == null ? "" : typed.toString().trim();
            if (title.isEmpty()) return;
            try (TaskStore store = new TaskStore(context)) {
                TaskNotifications.show(context, store.add(title));
            }
            TaskNotifications.showQuickAdd(context);
            return;
        }

        long id = intent.getLongExtra(TaskNotifications.EXTRA_ID, -1);
        if (id <= 0) return;
        if (TaskNotifications.ACTION_REMINDER.equals(action)) {
            ReminderAudit.note(context,"alarm_callback",id,0);
            ReminderScheduler.deliverDue(context, "alarm");
        } else if (TaskNotifications.ACTION_DONE.equals(action)) {
            try (TaskStore store = new TaskStore(context)) {
                TaskStore.Completion completion = store.complete(id);
                ReminderScheduler.cancel(context, id);
                TaskNotifications.cancel(context, id);
                if (completion != null && completion.nextTask != null) {
                    TaskNotifications.show(context, completion.nextTask);
                    ReminderScheduler.schedule(context, completion.nextTask);
                }
                if(completion != null) TaskNotifications.showUndo(context,id);
            }
        } else if (TaskNotifications.ACTION_UNDO.equals(action)) {
            try(TaskStore store=new TaskStore(context)) {
                long next=store.undoCompletion(id);
                if(next>0) { ReminderScheduler.cancel(context,next); TaskNotifications.cancel(context,next); }
                TaskNotifications.cancel(context,id);
                TaskNotifications.show(context,store.find(id)); ReminderScheduler.schedule(context,store.find(id));
            } catch(IllegalArgumentException e) { android.widget.Toast.makeText(context,e.getMessage(),android.widget.Toast.LENGTH_LONG).show(); }
        } else if (TaskNotifications.ACTION_DISMISSED.equals(action)) {
            // Ongoing flags are advisory on Android 14+; reinstate an unfinished
            // task after an individual swipe in both Phone+watch and Phone-only modes.
            // Programmatic cancellation after Done or Snooze does not invoke deleteIntent.
            final boolean dueCard=intent.getBooleanExtra(TaskNotifications.EXTRA_DISMISSED_DUE,false);
            PendingResult pending=goAsync();
            Context app=context.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                try {
                    TaskNotifications.restoreDismissed(app,id,dueCard);
                } catch(RuntimeException failure) {
                    android.util.Log.w("PinTask","Unable to restore dismissed task "+id,failure);
                } finally {
                    if(pending!=null)pending.finish();
                }
            },850);
        }
    }
}
