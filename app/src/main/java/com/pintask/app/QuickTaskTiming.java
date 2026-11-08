package com.pintask.app;

/** One shared calculation for Quick Add preview, validation and saving.
 * Relative delays start when Add is tapped; calendar timestamps stay exact. */
final class QuickTaskTiming {
    private QuickTaskTiming(){}
    static long dueAt(int delaySeconds,long exactDueAt,long now) {
        if(delaySeconds<0)throw new IllegalArgumentException("Invalid countdown");
        if(exactDueAt>0)return exactDueAt;
        if(delaySeconds==0)return 0;
        return now+delaySeconds*1000L;
    }
    static long alertAt(long dueAt,int minutesBefore) {
        if(dueAt<=0)return 0;
        if(minutesBefore<0 || minutesBefore>1440)
            throw new IllegalArgumentException("Choose an early reminder between 0 and 1440 minutes.");
        return dueAt-minutesBefore*60_000L;
    }
    static void validate(long dueAt,int minutesBefore,long now) {
        if(dueAt<=0){
            if(minutesBefore!=0)throw new IllegalArgumentException("Choose a task time before an early reminder.");
            return;
        }
        if(dueAt<=now)throw new IllegalArgumentException("Choose a future task time.");
        if(alertAt(dueAt,minutesBefore)<=now)
            throw new IllegalArgumentException("The early alert would be in the past. Choose a smaller advance or a later task time.");
    }
}
