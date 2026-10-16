package com.pintask.app;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;

/** Uses existing job/patient groups and shift links; no task migration or deletion. */
final class TaskSections {
    static android.content.Intent openIntent(Context context,long id) {
        android.content.Intent intent=new android.content.Intent(context,ShiftActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(id>0)intent.putExtra("open_task",id).setData(android.net.Uri.parse("pintask://task/"+id));return intent;
    }
    static boolean isWork(TaskStore.Task task) { return task.shiftRunId>0 || !task.group.isEmpty(); }
    static List<TaskStore.Task> personal(List<TaskStore.Task> tasks) {
        List<TaskStore.Task> result=new ArrayList<>();for(TaskStore.Task task:tasks)if(!isWork(task))result.add(task);return result;
    }
    static List<TaskStore.Task> work(Context context,List<TaskStore.Task> tasks) {
        List<TaskStore.Task> result=new ArrayList<>();for(TaskStore.Task task:tasks)if(isWork(task) && TaskGroups.matches(context,task.group))result.add(task);return result;
    }
    static TaskStore.Task addPersonal(TaskStore store,String title) {
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{TaskStore.Task task=store.add(title,1,0,0,-1);task=store.setGroup(task.id,"");db.setTransactionSuccessful();return task;}finally{db.endTransaction();}
    }
}
