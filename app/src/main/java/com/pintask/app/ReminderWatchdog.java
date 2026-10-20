package com.pintask.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Independent recovery path. AlarmManager remains responsible for on-time alerts;
 * this 15-minute periodic job is only a best-effort second opportunity when a
 * device suppresses the alarm callback, and is itself subject to OS delays.
 */
public final class ReminderWatchdog extends JobService {
    static final int JOB_ID=0x50494e54;
    static final long PERIOD=15*60*1000L;

    static void ensureScheduled(Context context) {
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        if(jobs==null)return;
        if(jobs.getPendingJob(JOB_ID)!=null)return;
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(context,ReminderWatchdog.class))
                .setPersisted(true)
                .setPeriodic(PERIOD)
                .build();
        int result=jobs.schedule(info);
        if(result!=JobScheduler.RESULT_SUCCESS)
            android.util.Log.w("PinTask","Background reminder recovery job could not be scheduled");
    }

    static void check(Context context) {
        ReminderAudit.note(context,"watchdog_check",0,0);
        ReminderScheduler.deliverDue(context, "watchdog");
        ReminderScheduler.rescheduleAll(context);
        TaskNotifications.syncAll(context);
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(()->{
            try{check(getApplicationContext());}
            catch(RuntimeException error){
                android.util.Log.w("PinTask","Background reminder recovery failed",error);
                ReminderAudit.note(getApplicationContext(),"watchdog_error",0,0);
            }finally{jobFinished(params,false);}
        },"PinTask-reminder-recovery").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
