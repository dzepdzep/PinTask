package com.pintask.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.widget.Toast;
import java.time.*;
import java.util.Calendar;
import java.util.function.LongConsumer;

/** Quick Add calendar scheduling. The local date and wall-clock time are
 * explicitly selected; unlike a stopwatch these do not depend on save time. */
final class QuickDateTimePicker {
    private QuickDateTimePicker(){}

    static long at(LocalDate date,int hour,int minute,ZoneId zone,long nowMillis){
        if(date==null||zone==null||hour<0||hour>23||minute<0||minute>59)
            throw new IllegalArgumentException("Choose a valid date and time.");
        LocalDateTime requested=date.atTime(hour,minute);
        ZonedDateTime result=requested.atZone(zone);
        // A missing hour at the spring DST transition must not silently move
        // an alarm from e.g. 02:30 to 03:30.
        if(!result.toLocalDateTime().equals(requested))
            throw new IllegalArgumentException("This local time does not exist because clocks change. Choose a different time.");
        long timestamp=result.toInstant().toEpochMilli();
        if(timestamp<=nowMillis)
            throw new IllegalArgumentException("Choose a future date and time.");
        return timestamp;
    }

    static void tomorrow(Activity activity,LongConsumer chosen){
        LocalDate date=LocalDate.now().plusDays(1);
        time(activity,date,9,0,chosen);
    }

    static void dateTime(Activity activity,LongConsumer chosen){
        LocalDate tomorrow=LocalDate.now().plusDays(1);
        DatePickerDialog calendar=new DatePickerDialog(activity,(picker,y,m,d)->
                time(activity,LocalDate.of(y,m+1,d),9,0,chosen),
                tomorrow.getYear(),tomorrow.getMonthValue()-1,tomorrow.getDayOfMonth());
        // Limit only past dates. Unlike countdowns, calendar tasks can be
        // planned well beyond 30 days (e.g. an appointment next year).
        Calendar min=Calendar.getInstance();
        min.set(Calendar.HOUR_OF_DAY,0);min.set(Calendar.MINUTE,0);
        min.set(Calendar.SECOND,0);min.set(Calendar.MILLISECOND,0);
        calendar.getDatePicker().setMinDate(min.getTimeInMillis());
        calendar.show();
    }

    private static void time(Activity activity,LocalDate date,int hour,int minute,LongConsumer chosen){
        TimePickerDialog dialog=new TimePickerDialog(activity,(picker,h,m)->{
            try{
                chosen.accept(at(date,h,m,ZoneId.systemDefault(),System.currentTimeMillis()));
            }catch(IllegalArgumentException problem){
                Toast.makeText(activity,problem.getMessage(),Toast.LENGTH_LONG).show();
                // Do not silently schedule in the past: allow choosing again.
                time(activity,date,h,m,chosen);
            }
        },hour,minute,android.text.format.DateFormat.is24HourFormat(activity));
        dialog.setTitle("Alert on "+date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")));
        dialog.show();
    }
}
