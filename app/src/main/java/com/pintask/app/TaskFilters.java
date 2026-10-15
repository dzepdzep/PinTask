package com.pintask.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class TaskFilters {
    private static String checklistText(String value) {
        StringBuilder text=new StringBuilder();org.json.JSONArray rows=TaskDetails.parse(value);for(int i=0;i<rows.length();i++)text.append(' ').append(rows.optJSONObject(i).optString("text"));return text.toString();
    }
    static List<TaskStore.Task> scoped(android.content.Context context,List<TaskStore.Task> tasks,String query,int filter,boolean all,long now) {
        return apply(all?tasks:TaskGroups.filter(context,tasks),query,filter,now);
    }
    static List<TaskStore.Task> apply(List<TaskStore.Task> tasks,String search,int filter,long now) {
        List<TaskStore.Task> result = new ArrayList<>();
        String query = search.trim().toLowerCase(Locale.ROOT);
        Calendar day = Calendar.getInstance(); day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY,0); day.set(Calendar.MINUTE,0); day.set(Calendar.SECOND,0); day.set(Calendar.MILLISECOND,0);
        long start = day.getTimeInMillis(); day.add(Calendar.DAY_OF_MONTH,1); long end = day.getTimeInMillis();
        for (TaskStore.Task task : tasks) {
            if (!(task.title+" "+task.notes+" "+task.group+" "+checklistText(task.checklist)).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (filter==1 && (task.done || task.dueAt==0 || task.dueAt>=now)) continue;
            if (filter==2 && (task.dueAt<start || task.dueAt>=end)) continue;
            if (filter==3 && task.dueAt!=0) continue;
            if (filter>=4 && task.priority!=6-filter) continue;
            result.add(task);
        }
        return result;
    }
}
