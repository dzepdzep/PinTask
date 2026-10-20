package com.pintask.app;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.*;

/**
 * Conservative offline interpretation of Android's speech transcription.
 * Never silently guesses an unspoken clock time. All results require review.
 */
final class VoiceTaskParser {
    static final class Result {
        final String phrase,title,note;
        final LocalDate date;
        final LocalTime time; // null when time still needs explicit user selection
        final int recurrence;
        final boolean scheduleMentioned;
        Result(String phrase,String title,LocalDate date,LocalTime time,
               int recurrence,boolean scheduleMentioned,String note){
            this.phrase=phrase;this.title=title;this.date=date;this.time=time;
            this.recurrence=recurrence;this.scheduleMentioned=scheduleMentioned;this.note=note;
        }
    }
    private static final String WEEKDAYS=
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag";
    private static final Pattern DAY=Pattern.compile(
            "\\b(?:(every\\s+other|every|next|jeden\\s+zweiten|jeden|nächsten)\\s+)?("+WEEKDAYS+")\\b");
    private static final Pattern CLOCK=Pattern.compile(
            "\\b(?:at|um)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|uhr)?\\b");
    private static final Pattern RELATIVE=Pattern.compile(
            "\\bin\\s+(\\d{1,4})\\s*(hours?|hrs?|stunden?|minutes?|mins?|minuten?)\\b");
    private static final Pattern HOURLY=Pattern.compile(
            "\\b(?:every\\s+(?:(\\d{1,2})\\s*)?hours?|hourly|alle\\s+(?:(\\d{1,2})\\s*)?stunden?)\\b");
    private VoiceTaskParser(){}

    static Result parse(String speech,ZonedDateTime now){
        if(speech==null||speech.trim().isEmpty())
            throw new IllegalArgumentException("No words recognized. Try again or type the command.");
        String text=speech.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[!?]"," ").replaceAll("\\s+"," ");
        LocalDate day=now.toLocalDate();
        LocalTime time=null;
        boolean scheduled=false;
        int repeat=TaskStore.REPEAT_NEVER;
        String note="";
        Matcher match=DAY.matcher(text);
        if(match.find()){
            DayOfWeek weekday=weekday(match.group(2));
            String prefix=match.group(1)==null?"":match.group(1);
            if(prefix.equals("every other")||prefix.equals("jeden zweiten"))
                repeat=TaskStore.REPEAT_FORTNIGHTLY;
            else if(prefix.equals("every")||prefix.equals("jeden"))
                repeat=TaskStore.REPEAT_WEEKLY;
            // Explicit "next Tuesday" means the next calendar Tuesday, not today.
            day=day.with(prefix.equals("next")||prefix.equals("nächsten")?
                    TemporalAdjusters.next(weekday):TemporalAdjusters.nextOrSame(weekday));
            text=replaceMatch(text,match);
            scheduled=true;
        }

        boolean dayAfter=contains(text,"day after tomorrow")||contains(text,"übermorgen");
        if(dayAfter){
            day=now.toLocalDate().plusDays(2);
            text=text.replaceAll("\\b(day after tomorrow|übermorgen)\\b"," ");
            scheduled=true;
        }else if(contains(text,"tomorrow")||contains(text,"morgen")){
            day=now.toLocalDate().plusDays(1);
            text=text.replaceAll("\\b(tomorrow|morgen)\\b"," ");
            scheduled=true;
        }else if(contains(text,"today")||contains(text,"heute")){
            day=now.toLocalDate();
            text=text.replaceAll("\\b(today|heute)\\b"," ");
            scheduled=true;
        }

        Matcher hourly=HOURLY.matcher(text);
        int intervalHours=0;
        if(hourly.find()){
            String n=hourly.group(1)!=null?hourly.group(1):hourly.group(2);
            intervalHours=n==null?1:Integer.parseInt(n);
            if(intervalHours<1||intervalHours>24)
                throw new IllegalArgumentException("Hourly repeats must be between 1 and 24 hours.");
            repeat=5000+intervalHours;
            text=replaceMatch(text,hourly);
            scheduled=true;
        }else{
            Pattern daily=Pattern.compile(
                "\\b(every day|daily|each day|jeden tag|täglich)\\b");
            Matcher m=daily.matcher(text);
            if(m.find()){repeat=TaskStore.REPEAT_DAILY;scheduled=true;text=replaceMatch(text,m);}
        }

        Matcher clock=CLOCK.matcher(text);
        if(clock.find()){
            int hour=Integer.parseInt(clock.group(1));
            int minute=clock.group(2)==null?0:Integer.parseInt(clock.group(2));
            String meridiem=clock.group(3)==null?"":clock.group(3);
            if(minute>59||hour>24||(hour==24&&minute!=0))
                throw new IllegalArgumentException("That time is invalid. Choose a clock time in the review.");
            if(meridiem.equals("am")||meridiem.equals("pm")){
                if(hour<1||hour>12)throw new IllegalArgumentException("Use 1–12 with AM/PM.");
                hour=(hour%12)+(meridiem.equals("pm")?12:0);
            }
            if(hour==24){
                day=day.plusDays(1);
                hour=0;
                note="24:00 means 00:00 at the END of the named day. Check the date before saving.";
            }
            time=LocalTime.of(hour,minute);
            text=replaceMatch(text,clock);scheduled=true;
        }else{
            Matcher relative=RELATIVE.matcher(text);
            if(relative.find()){
                int amount=Integer.parseInt(relative.group(1));
                if(amount<1)throw new IllegalArgumentException("Choose a future duration.");
                String unit=relative.group(2);
                ZonedDateTime due=unit.startsWith("h")||unit.startsWith("stund")?
                        now.plusHours(amount):now.plusMinutes(amount);
                day=due.toLocalDate();time=due.toLocalTime().withNano(0);
                text=replaceMatch(text,relative);scheduled=true;
            }else if(intervalHours>0){
                ZonedDateTime due=now.plusHours(intervalHours);
                day=due.toLocalDate();time=due.toLocalTime().withNano(0);
            }
        }

        if(!scheduled && text.matches(".*\\b(next|every|repeat|weekly|hourly|tuesday|wednesday|thursday|friday|monday|saturday|sunday|tonight|later|nächste|jeden|alle|uhr)\\b.*")){
            scheduled=true;
            note="Some scheduling words were not understood. Choose the correct date, time and repeat in the review.";
        }

        String title=text.replaceAll(
                "^(please\\s+)?(remind me to|remind me|i need to|i need|i have to|i want to|ich muss|erinnere mich daran|erinnere mich|bitte)\\s+","")
                .replaceAll("^(to|dass ich)\\s+","")
                .replaceAll("\\b(please|bitte)\\s*$","")
                .replaceAll("[.,;]+$","").replaceAll("\\s+"," ").trim();
        return new Result(speech,title,day,time,repeat,scheduled,note);
    }
    private static String replaceMatch(String source,Matcher matcher){
        return source.substring(0,matcher.start())+" "+source.substring(matcher.end());
    }
    private static boolean contains(String source,String term){
        return Pattern.compile("\\b"+Pattern.quote(term)+"\\b").matcher(source).find();
    }
    private static DayOfWeek weekday(String word){
        switch(word){
            case "monday":case "montag":return DayOfWeek.MONDAY;
            case "tuesday":case "dienstag":return DayOfWeek.TUESDAY;
            case "wednesday":case "mittwoch":return DayOfWeek.WEDNESDAY;
            case "thursday":case "donnerstag":return DayOfWeek.THURSDAY;
            case "friday":case "freitag":return DayOfWeek.FRIDAY;
            case "saturday":case "samstag":return DayOfWeek.SATURDAY;
            default:return DayOfWeek.SUNDAY;
        }
    }
}
