package com.pintask.app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A single mutable, inspectable draft behind the speech review controls. */
final class VoiceReviewModel {
    private final SpokenTask parsed;
    private final ZoneId zone;
    private LocalDate day;
    private LocalTime time;
    private int repeatCode;
    private int notifyBeforeMinutes;
    private boolean editedRepeat;
    private boolean editedDateOrTime;

    VoiceReviewModel(SpokenTask parsed,ZoneId zone) {
        this.parsed=parsed;
        this.zone=zone;
        this.repeatCode=parsed.repeatCode;
        this.notifyBeforeMinutes=parsed.remindBeforeMinutes;
        if(parsed.dueAt>0){
            ZonedDateTime date=Instant.ofEpochMilli(parsed.dueAt).atZone(zone);
            day=date.toLocalDate();
            time=date.toLocalTime();
        }
    }

    LocalDate day(){return day;}
    LocalTime time(){return time;}
    int repeatCode(){return repeatCode;}
    int notifyBeforeMinutes(){return notifyBeforeMinutes;}
    void changeDate(LocalDate next){
        if(next==null)throw new IllegalArgumentException("Choose a date");
        day=next;editedDateOrTime=true;
    }
    void changeTime(LocalTime next,LocalDate today){
        if(next==null)throw new IllegalArgumentException("Choose a time");
        if(day==null)day=today;
        time=next.withSecond(0).withNano(0);
        editedDateOrTime=true;
    }
    void changeRepeat(int next){
        if(!RepeatRules.valid(next))throw new IllegalArgumentException("Unsupported repeat");
        repeatCode=next;editedRepeat=true;
    }

    void changeAdvance(int minutes){
        if(minutes<0||minutes>1440)throw new IllegalArgumentException("Choose 0 to 1440 minutes before the task.");
        notifyBeforeMinutes=minutes;
    }
    String advanceLabel(){
        if(notifyBeforeMinutes==0)return "At task time";
        if(notifyBeforeMinutes==30)return "30 minutes before";
        if(notifyBeforeMinutes==60)return "1 hour before";
        if(notifyBeforeMinutes%60==0)return (notifyBeforeMinutes/60)+" hours before";
        return notifyBeforeMinutes+" minutes before";
    }
    String alarmLabel(){
        if(day==null||time==null)return "Choose a task time for advance reminders.";
        ZonedDateTime due=ZonedDateTime.of(day,time,zone);
        if(!due.toLocalDate().equals(day)||due.getHour()!=time.getHour()||
                due.getMinute()!=time.getMinute())return "Check the selected date/time.";
        ZonedDateTime alarm=due.minusMinutes(notifyBeforeMinutes);
        return "Alarm rings: "+alarm.format(
                DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm",Locale.ENGLISH))+
                (notifyBeforeMinutes==0?"":" ("+advanceLabel()+")");
    }

    String dateLabel(){
        return day==null?"Not scheduled":day.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy",Locale.ENGLISH));
    }
    String timeLabel(){
        if(time==null)return day==null?"Now (pinned task)":"Not set · choose a time";
        return time.format(DateTimeFormatter.ofPattern(
                time.getSecond()!=0?"HH:mm:ss":"HH:mm",Locale.ROOT));
    }
    String repeatLabel(){
        if(repeatCode==0)return "Does not repeat";
        if(repeatCode==TaskStore.REPEAT_FORTNIGHTLY&&!editedRepeat &&
                parsed.original.matches("(?is).*every other (monday|tuesday|wednesday|thursday|friday|saturday|sunday).*")){
            if(day!=null)return "Every other "+day.getDayOfWeek().toString().substring(0,1)+
                    day.getDayOfWeek().toString().substring(1).toLowerCase(Locale.ROOT);
        }
        return RepeatRules.label(repeatCode);
    }

    String preview(){
        if(day==null&&time==null)return "Immediately pinned until Done";
        if(day==null||time==null)return "Choose both date and time to schedule the alert.";
        return "Task due: "+dateLabel()+" at "+timeLabel()+
                "\n"+alarmLabel();
    }

    long validatedDueAt(long nowMillis){
        if(day==null && time==null){
            if(notifyBeforeMinutes>0)throw new IllegalArgumentException("An advance reminder needs a date and time.");
            if(repeatCode!=0)throw new IllegalArgumentException("Repeating tasks need a date and time.");
            return 0;
        }
        if(day==null||time==null)throw new IllegalArgumentException("Choose a date and a time.");
        ZonedDateTime resolved=ZonedDateTime.of(day,time,zone);
        if(!resolved.toLocalDate().equals(day)||resolved.getHour()!=time.getHour()||
                resolved.getMinute()!=time.getMinute())
            throw new IllegalArgumentException("That clock time does not exist on the selected date (daylight saving time).");
        long due=editedDateOrTime?resolved.toInstant().toEpochMilli():parsed.dueAt;
        if(due<=nowMillis)throw new IllegalArgumentException("The chosen task time has passed. Change the date or time.");
        if(due-notifyBeforeMinutes*60_000L<=nowMillis)
            throw new IllegalArgumentException("The advance reminder time has already passed. Choose a later task time or a shorter advance.");
        int mask=RepeatRules.days(repeatCode);
        if(mask!=0){
            int bit=1<<(day.getDayOfWeek().getValue()-1);
            if((mask&bit)==0)
                throw new IllegalArgumentException("The date doesn't match "+repeatLabel()+". Change its date or repeat pattern.");
        }
        return due;
    }
}
