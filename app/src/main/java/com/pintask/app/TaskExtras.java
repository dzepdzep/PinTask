package com.pintask.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

final class TaskExtras {
    static void tomorrow(Activity activity,TaskStore.Task task,Runnable refresh) {
        try(TaskStore store=new TaskStore(activity)) {
            TaskStore.Task before=store.find(task.id);if(before==null)return;
            TaskStore.Task updated=store.tomorrow(task.id);sync(activity,updated);refresh.run();
            LinearLayout body=new LinearLayout(activity);body.setBackgroundColor(Color.rgb(35,50,40));body.setPadding(16,12,16,12);body.setGravity(Gravity.CENTER_VERTICAL);
            TextView label=new TextView(activity);label.setText("Moved to tomorrow");label.setTextColor(Color.WHITE);body.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button undo=new Button(activity);undo.setText("Undo");body.addView(undo);
            PopupWindow popup=new PopupWindow(body,-1,-2,false);long expires=System.currentTimeMillis()+10000;
            undo.setOnClickListener(v->{try(TaskStore tasks=new TaskStore(activity)){if(System.currentTimeMillis()>expires)throw new IllegalArgumentException("Undo expired");sync(activity,tasks.undoTomorrow(before,updated.dueAt));refresh.run();}catch(IllegalArgumentException e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_SHORT).show();}popup.dismiss();});
            android.view.View decor=activity.getWindow().getDecorView();
            decor.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(android.view.View v){}public void onViewDetachedFromWindow(android.view.View v){popup.dismiss();v.removeOnAttachStateChangeListener(this);}});
            popup.showAtLocation(decor,Gravity.BOTTOM,0,0);body.postDelayed(popup::dismiss,10000);
        }catch(IllegalArgumentException e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    static void sync(Activity activity,TaskStore.Task task) {
        ReminderScheduler.cancel(activity,task.id);TaskNotifications.cancel(activity,task.id);TaskNotifications.show(activity,task);ReminderScheduler.schedule(activity,task);
    }
}
