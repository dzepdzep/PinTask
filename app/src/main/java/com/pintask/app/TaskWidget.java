package com.pintask.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.List;

public final class TaskWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { update(context, manager, ids); }
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context,TaskWidget.class));
        if (ids.length > 0) update(context,manager,ids);
    }
    private static void update(Context context,AppWidgetManager manager,int[] ids) {
        try (TaskStore store = new TaskStore(context)) {
            List<TaskStore.Task> tasks = store.activePersonal();
            for (int id : ids) {
                RemoteViews view = new RemoteViews(context.getPackageName(),R.layout.task_widget);
                view.setTextViewText(R.id.widget_title,"PinTask · " + tasks.size() + " tasks");
                view.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
                view.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context,0,new Intent(context,QuickAddActivity.class),PendingIntent.FLAG_IMMUTABLE));
                view.removeAllViews(R.id.widget_tasks);
                if (tasks.isEmpty()) view.setTextViewText(R.id.widget_empty,"All done. Add a task above.");
                else view.setTextViewText(R.id.widget_empty,tasks.size() > 5 ? "Showing 5 · Tap PinTask for all tasks" : "Tap Done to complete a task");
                for (int i=0;i<Math.min(5,tasks.size());i++) {
                    TaskStore.Task task = tasks.get(i);
                    RemoteViews row = new RemoteViews(context.getPackageName(),R.layout.widget_task);
                    row.setTextViewText(R.id.widget_task_title,task.title);
                    row.setOnClickPendingIntent(R.id.widget_task_title, PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
                    Intent done = new Intent(context,TaskActionReceiver.class).setAction(TaskNotifications.ACTION_DONE).putExtra(TaskNotifications.EXTRA_ID,task.id);
                    row.setOnClickPendingIntent(R.id.widget_done,PendingIntent.getBroadcast(context,(int)task.id,done,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
                    view.addView(R.id.widget_tasks,row);
                }
                manager.updateAppWidget(id,view);
            }
        }
    }
}
