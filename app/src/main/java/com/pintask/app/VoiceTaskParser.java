package com.pintask.app;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deliberately conservative, local text-to-schedule parser. Transcription is
 * supplied by Android; no AI or remote task data is sent by PinTask.
 * Unrecognized words remain in the editable title; scheduling is previewed,
 * never silently guessed or saved.
 */
final class VoiceTaskParser {
    private VoiceTaskParser() {}
    private static final String WEEKDAY=
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday|"+
            "montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|"+
            "ponedjeljak|utorak|srijeda|četvrtak|cetvrtak|petak|subota|nedjelja";
    private static final Pattern ALTERNATING=Pattern.compile(
            "\\b(?:every\\s+(?:other|second)|jeden\\s+zweiten|jeden\\s+zweite[nr]?|svaki\\s+drugi|svake\\s+druge)\\s+("+WEEKDAY+")\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern WEEKLY=Pattern.compile(
            "\\b(?:every|jeden|svaki|svake)\\s+("+WEEKDAY+")\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern SINGLE_DAY=Pattern.compile(
            "\\b(?:(?:on|this|next|am|na|u)\\s+)?("+WEEKDAY+")\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern DAILY=Pattern.compile(
            "\\b(?:every\\s+day|daily|jeden\\s+tag|täglich|taeglich|svaki\\s+dan|svakog\\s+dana)\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern HOURLY=Pattern.compile(
            "\\b(?:every\\s+(\\d{1,2}|one|two|three|four)?\\s*hours?|jede\\s+(\\d{1,2})\\s+stunden?|svakih\\s+(\\d{1,2})\\s+sati)\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern TODAY=Pattern.compile("\\b(today|heute|danas)\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern TOMORROW=Pattern.compile("\\b(tomorrow|morgen|sutra)\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern CLOCK=Pattern.compile(
            "\\b(?:at|um|u)\\s+(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm|uhr|sati)?\\b",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern COLON_CLOCK=Pattern.compile(
            "\\b(\\d{1,2}):(\\d{2})\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX=Pattern.compile(
            "^(?:(?:please\\s+)?(?:remind\\s+me\\s+to|i\\s+need\\s+to|i\\s+have\\s+to|"+
            "ich\\s+muss|erinnere\\s+mich(?:\\s+daran)?|moram|podsjeti\\s+me\\s+da)\\s+)",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);

    static final class Draft {
        final String transcript;
        String title;
        LocalDate day;
        int hour=-1,minute=0,recurrence=TaskStore.REPEAT_NEVER;
        boolean midnightNeedsConfirmation;
        boolean explicitDate;
        boolean recognizedSchedule;
        String warning="";
        Draft(String text){transcript=text;}
        boolean hasTime(){return hour>=0;}
        long dueMillis(ZoneId zone){
            if(!hasTime()||day==null)throw new IllegalArgumentException("Select a date and time");
            return day.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli();
        }
    }

    static Draft parse(String spoken,ZonedDateTime now){
        if(spoken==null||spoken.trim().isEmpty())throw new IllegalArgumentException("Nothing recognized");
        String text=spoken.trim().replaceAll("[.!?]+$","").replaceAll("\\s+"," ");
        Draft out=new Draft(spoken.trim());
        Matcher m;
        m=ALTERNATING.matcher(text);
        if(m.find()){out.recurrence=TaskStore.REPEAT_FORTNIGHTLY;
            out.day=now.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday(m.group(1))));
            out.recognizedSchedule=true;text=remove(text,m);}
        else {m=WEEKLY.matcher(text);
            if(m.find()){out.recurrence=TaskStore.REPEAT_WEEKLY;
                out.day=now.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday(m.group(1))));
                out.recognizedSchedule=true;text=remove(text,m);}
            else {m=SINGLE_DAY.matcher(text);
                if(m.find()){out.day=now.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday(m.group(1))));
                    out.explicitDate=true;out.recognizedSchedule=true;text=remove(text,m);}}
        }
        m=DAILY.matcher(text);
        if(m.find()){out.recurrence=TaskStore.REPEAT_DAILY;out.recognizedSchedule=true;text=remove(text,m);}
        m=HOURLY.matcher(text);
        if(m.find()){
            String quantity=m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3);
            int count=quantity==null||quantity.isEmpty()?1:wordCount(quantity);
            if(count<1||count>24)throw new IllegalArgumentException("Choose 1 to 24 hours");
            out.recurrence=5000+count;out.recognizedSchedule=true;text=remove(text,m);
        }
        m=TOMORROW.matcher(text);
        if(m.find()){out.day=now.toLocalDate().plusDays(1);out.explicitDate=true;out.recognizedSchedule=true;text=remove(text,m);}
        else {m=TODAY.matcher(text);
            if(m.find()){out.day=now.toLocalDate();out.explicitDate=true;out.recognizedSchedule=true;text=remove(text,m);}}
        m=CLOCK.matcher(text);
        if(!m.find()){m=COLON_CLOCK.matcher(text);if(m.find()){out.hour=parseHour(m.group(1),m.group(2),null,out);out.minute=Integer.parseInt(m.group(2));out.recognizedSchedule=true;text=remove(text,m);}}
        else {
            String minutes=m.group(2);String modifier=m.group(3);
            out.hour=parseHour(m.group(1),minutes,modifier,out);
            out.minute=minutes==null?0:Integer.parseInt(minutes);
            out.recognizedSchedule=true;text=remove(text,m);
        }
        if(out.hour==24){
            // 24:00 denotes end of the selected date, NOT its beginning.
            // A dedicated confirmation screen resolves this ambiguity.
            out.midnightNeedsConfirmation=true;
            out.hour=0;out.minute=0;
            if(out.day==null)out.day=now.toLocalDate();
        }
        if(out.hour>=0 && out.day==null)out.day=now.toLocalDate();
        if(out.recurrence>=5001 && out.recurrence<=5024){
            // Hourly routine counts from Save, with no invented clock time.
            out.day=now.toLocalDate();
            out.hour=now.getHour();out.minute=now.getMinute();
        }
        out.title=PREFIX.matcher(text.replaceAll("\\s+"," ").trim()).replaceFirst("").trim()
                .replaceAll("(?i)\\s+(?:on|at|um|am|u|to)$","")
                .replaceAll("\\s+"," ").trim();
        if(out.title.isEmpty())out.warning="Please enter what you want to do.";
        else if(out.midnightNeedsConfirmation)out.warning="Confirm whether 24:00 means the end or start of the chosen day.";
        else if(!out.hasTime() && out.recurrence>0)out.warning="Choose a time for this repeating task.";
        else if(out.explicitDate&&!out.hasTime())out.warning="Choose a time for this date.";
        else if(out.hasTime() && out.recurrence!=0 && out.day!=null
                && out.dueMillis(now.getZone())<=now.toInstant().toEpochMilli())
            out.warning="The first occurrence is in the past; choose a future time.";
        return out;
    }
    private static int parseHour(String raw,String mins,String modifier,Draft out){
        int hour=Integer.parseInt(raw),minute=mins==null?0:Integer.parseInt(mins);
        if(minute>59)throw new IllegalArgumentException("Minutes must be 00–59");
        if(modifier!=null && modifier.equalsIgnoreCase("pm")){
            if(hour<1||hour>12)throw new IllegalArgumentException("Invalid PM clock time");
            if(hour!=12)hour+=12;
        }else if(modifier!=null && modifier.equalsIgnoreCase("am")){
            if(hour<1||hour>12)throw new IllegalArgumentException("Invalid AM clock time");
            if(hour==12)hour=0;
        }
        if(hour>24||(hour==24 && minute!=0))throw new IllegalArgumentException("Choose a time from 00:00 to 24:00");
        return hour;
    }
    private static String remove(String value,Matcher matcher){
        return (value.substring(0,matcher.start())+" "+value.substring(matcher.end())).replaceAll("\\s+"," ").trim();
    }
    private static int wordCount(String v){
        switch(v.toLowerCase(Locale.ROOT)){
            case "one":return 1;case "two":return 2;
            case "three":return 3;case "four":return 4;
            default:return Integer.parseInt(v);
        }
    }
    private static DayOfWeek weekday(String word){
        String s=word.toLowerCase(Locale.ROOT);
        if(s.matches("monday|montag|ponedjeljak"))return DayOfWeek.MONDAY;
        if(s.matches("tuesday|dienstag|utorak"))return DayOfWeek.TUESDAY;
        if(s.matches("wednesday|mittwoch|srijeda"))return DayOfWeek.WEDNESDAY;
        if(s.matches("thursday|donnerstag|četvrtak|cetvrtak"))return DayOfWeek.THURSDAY;
        if(s.matches("friday|freitag|petak"))return DayOfWeek.FRIDAY;
        if(s.matches("saturday|samstag|subota"))return DayOfWeek.SATURDAY;
        return DayOfWeek.SUNDAY;
    }
}
