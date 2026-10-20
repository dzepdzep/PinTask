package com.pintask.app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, conservative English command parser. It never guesses an unclear
 * time/date. Speech recognition is delegated to Android's installed recognizer.
 */
final class SpokenTask {
    final String title;
    final long dueAt;
    final int repeatCode;
    final int remindBeforeMinutes;
    final String original;
    final String schedule;
    final boolean isTimed;

    private SpokenTask(String title,long dueAt,int repeatCode,int remindBeforeMinutes,String original,String schedule) {
        this.title=title;this.dueAt=dueAt;this.repeatCode=repeatCode;this.remindBeforeMinutes=remindBeforeMinutes;this.original=original;
        this.schedule=schedule;this.isTimed=dueAt>0;
    }

    /** Keep the original localized transcript visible after schedule normalization. */
    SpokenTask withOriginal(String heard) {
        return new SpokenTask(title,dueAt,repeatCode,remindBeforeMinutes,heard,schedule);
    }

    private static final Pattern REPEAT=Pattern.compile(
        "\\b(every\\s+other\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|"+
        "every\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|"+
        "every\\s+other\\s+day|every\\s+(?:day|daily|hour|two\\s+weeks|2\\s+weeks)|"+
        "every\\s+\\d{1,2}\\s+hours?)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE=Pattern.compile(
        "\\b(today|tomorrow|next\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK=Pattern.compile(
        "\\bat\\s+(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm)?\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern DELAY=Pattern.compile(
        "\\bin\\s+(\\d+(?:[.,]\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern BEFORE=Pattern.compile(
        "\\b(?:(\\d{1,4}|one|five|ten|fifteen|twenty|thirty|forty|sixty)\\s*(minutes?|mins?|m|hours?|hrs?|h)|half\\s+(?:an?\\s+)?hour)\\s+(?:before(?:hand)?|earlier|early|in\\s+advance)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAD=Pattern.compile(
        "^(?:please\\s+)?(?:remind\\s+me\\s+to|i\\s+need\\s+to|i\\s+have\\s+to|"+
        "remember\\s+to|add\\s+(?:a\\s+)?task\\s+to|create\\s+(?:a\\s+)?task\\s+to|"+
        "remind\\s+me|i\\s+need|please)\\s+",Pattern.CASE_INSENSITIVE);

    static SpokenTask parse(String input,ZonedDateTime at) {
        if(input==null||input.trim().isEmpty())throw new IllegalArgumentException("Speak or enter a task first.");
        if(input.length()>700)throw new IllegalArgumentException("Use a shorter voice command.");
        String original=input.trim().replaceAll("\\s+"," ");
        String working=LEAD.matcher(original).replaceFirst("").trim();
        String lower=working.toLowerCase(Locale.ROOT);
        int early=0;
        Matcher before=BEFORE.matcher(lower);
        if(before.find()){
            String unit=before.group(2);
            if(unit==null) early=30;
            else {
                String count=before.group(1).toLowerCase(Locale.ROOT);
                int n;
                switch(count){
                    case "one":n=1;break;
                    case "five":n=5;break;
                    case "ten":n=10;break;
                    case "fifteen":n=15;break;
                    case "twenty":n=20;break;
                    case "thirty":n=30;break;
                    case "forty":n=40;break;
                    case "sixty":n=60;break;
                    default:n=Integer.parseInt(count);
                }
                early=n*(unit.toLowerCase(Locale.ROOT).startsWith("h")?60:1);
            }
            if(early<1||early>1440)
                throw new IllegalArgumentException("Choose 1 to 1440 minutes before the task.");
            working=remove(working,before.start(),before.end());
            lower=working.toLowerCase(Locale.ROOT);
            if(BEFORE.matcher(lower).find())
                throw new IllegalArgumentException("Choose only one advance reminder time.");
        }
        Matcher rm=REPEAT.matcher(lower);
        int repeat=0;
        DayOfWeek weekday=null;
        if(rm.find()) {
            String words=rm.group(1);
            if(words.equals("every other day"))repeat=5;
            else if(words.equals("every day")||words.equals("every daily"))repeat=TaskStore.REPEAT_DAILY;
            else if(words.equals("every hour"))repeat=5001;
            else if(words.equals("every two weeks")||words.equals("every 2 weeks"))repeat=TaskStore.REPEAT_FORTNIGHTLY;
            else if(words.matches("every \\d+ hours?")){
                int h=Integer.parseInt(words.replaceAll("[^0-9]",""));
                if(h<1||h>24)throw new IllegalArgumentException("Hourly routines support 1 to 24 hours.");
                repeat=5000+h;
            } else {
                weekday=day(words.substring(words.lastIndexOf(' ')+1));
                if(weekday==null)throw new IllegalArgumentException("Please specify a valid weekday.");
                repeat=words.startsWith("every other ")?TaskStore.REPEAT_FORTNIGHTLY:
                        4000+(1<<(weekday.getValue()-1));
            }
            working=remove(working,rm.start(),rm.end());lower=working.toLowerCase(Locale.ROOT);
        }
        Matcher duplicate=REPEAT.matcher(lower);
        if(duplicate.find())throw new IllegalArgumentException("Choose only one repeat pattern per task.");

        Matcher dateMatcher=DATE.matcher(lower);
        LocalDate date=null;
        boolean explicitDate=false;
        if(dateMatcher.find()){
            explicitDate=true;
            String text=dateMatcher.group(1);
            date=text.equals("today")?at.toLocalDate():text.equals("tomorrow")?
                    at.toLocalDate().plusDays(1):
                    nextWeekday(at.toLocalDate(),day(text.substring(5)),true);
            working=remove(working,dateMatcher.start(),dateMatcher.end());
            working=working.replaceAll("(?i)\\b(on|for)\\s+(?=at\\b|$)","");
            lower=working.toLowerCase(Locale.ROOT);
        }
        if(weekday!=null&&explicitDate)
            throw new IllegalArgumentException("Say the repeating weekday OR an exact date, not both.");

        Matcher clockMatcher=CLOCK.matcher(lower);
        LocalTime clock=null;
        if(clockMatcher.find()){
            int hour=Integer.parseInt(clockMatcher.group(1));
            int minute=clockMatcher.group(2)==null?0:Integer.parseInt(clockMatcher.group(2));
            String meridiem=clockMatcher.group(3);
            if(hour==24)throw new IllegalArgumentException(
                "‘24:00’ is ambiguous. Say ‘tomorrow at 00:00’ for the start of tomorrow, or specify the following date for midnight at its end.");
            if(minute>59 || hour>23 || (meridiem!=null&&(hour<1||hour>12)))
                throw new IllegalArgumentException("Please state a valid time, such as 13:00 or 8 am.");
            if(meridiem!=null){hour=hour%12;if(meridiem.equalsIgnoreCase("pm"))hour+=12;}
            clock=LocalTime.of(hour,minute);
            working=remove(working,clockMatcher.start(),clockMatcher.end());
            lower=working.toLowerCase(Locale.ROOT);
        }

        Matcher delayMatcher=DELAY.matcher(lower);
        Duration duration=null;
        if(delayMatcher.find()){
            double n=Double.parseDouble(delayMatcher.group(1).replace(',','.'));
            boolean hours=delayMatcher.group(2).startsWith("h");
            double seconds=n*(hours?3600:60);
            if(!Double.isFinite(seconds)||seconds<1||seconds>30d*24*3600||seconds!=Math.rint(seconds))
                throw new IllegalArgumentException("Choose a delay between 1 second and 30 days.");
            duration=Duration.ofSeconds((long)seconds);
            working=remove(working,delayMatcher.start(),delayMatcher.end());
            lower=working.toLowerCase(Locale.ROOT);
        }

        if(duration!=null&&(clock!=null||explicitDate||weekday!=null))
            throw new IllegalArgumentException("Use either a delay (‘in 30 minutes’) or a clock time, not both.");
        if(clock==null&&duration==null&&explicitDate)
            throw new IllegalArgumentException("Please add a time, such as ‘tomorrow at 13:00’.");
        if(early>0&&clock==null&&duration==null&&repeat==0)
            throw new IllegalArgumentException("Choose a task date/time before asking for an earlier alert.");
        if(repeat>0&&clock==null&&duration==null&&repeat<5000)
            throw new IllegalArgumentException("Please specify a time for your repeating reminder.");
        if(weekday!=null&&clock==null)
            throw new IllegalArgumentException("Please add a time, such as ‘every other Tuesday at 08:00’.");
        if(repeat>=5000&&clock==null&&duration==null)duration=Duration.ofHours(repeat-5000);
        if(Pattern.compile("(?i)\\b(?:every|tomorrow|today|next\\s+\\w+|at\\s+\\d+|in\\s+\\d+|before)\\b").matcher(working).find())
            throw new IllegalArgumentException("I couldn't understand the entire schedule. Please edit the command.");
        String title=working.replaceAll("(?i)\\b(?:on|for)\\s*$","")
                .replaceAll("^[,;\\s]+","")
                .replaceAll("(?i)^(?:to|on|for)\\s+","").trim()
                .replaceAll("[,.;!?]+$","").trim();
        if(title.isEmpty())throw new IllegalArgumentException("Please include what needs doing.");
        if(title.length()>160)throw new IllegalArgumentException("Task name must be 160 characters or less.");

        ZonedDateTime due=null;
        if(duration!=null)due=at.plus(duration);
        else if(clock!=null){
            LocalDate day=date!=null?date:at.toLocalDate();
            if(weekday!=null)day=nextWeekday(at.toLocalDate(),weekday,false);
            due=ZonedDateTime.of(day,clock,at.getZone());
            if(weekday!=null&& !due.isAfter(at))due=due.plusWeeks(1);
            else if(date==null&&weekday==null&&!due.isAfter(at))due=due.plusDays(1);
            else if(!due.isAfter(at))throw new IllegalArgumentException(
                    "That time has already passed. Please choose a future date or time.");
        }
        String schedule=due==null?"Now · pinned unfinished task":
                due.format(DateTimeFormatter.ofPattern("EEE dd MMM yyyy · HH:mm",Locale.ENGLISH))+
                (repeat>0?" · "+RepeatRules.label(repeat):" · One time");
        if(due==null&&repeat>0)throw new IllegalArgumentException("A repeating task must have a schedule.");
        if(due!=null&&early>0&&!due.toInstant().isAfter(at.toInstant().plusSeconds(early*60L)))
            throw new IllegalArgumentException("That early reminder would already be in the past. Choose a later task time or a shorter advance.");
        return new SpokenTask(title,due==null?0:due.toInstant().toEpochMilli(),repeat,early,original,schedule);
    }

    private static String remove(String value,int start,int end){
        return (value.substring(0,start)+value.substring(end)).replaceAll("\\s+"," ").trim();
    }
    private static DayOfWeek day(String value){
        switch(value.toLowerCase(Locale.ROOT)){
            case "monday":return DayOfWeek.MONDAY;
            case "tuesday":return DayOfWeek.TUESDAY;
            case "wednesday":return DayOfWeek.WEDNESDAY;
            case "thursday":return DayOfWeek.THURSDAY;
            case "friday":return DayOfWeek.FRIDAY;
            case "saturday":return DayOfWeek.SATURDAY;
            case "sunday":return DayOfWeek.SUNDAY;
            default:return null;
        }
    }
    private static LocalDate nextWeekday(LocalDate start,DayOfWeek weekday,boolean strictlyNext){
        if(weekday==null)throw new IllegalArgumentException("Unknown weekday.");
        int offset=(weekday.getValue()-start.getDayOfWeek().getValue()+7)%7;
        if(strictlyNext&&offset==0)offset=7;
        return start.plusDays(offset);
    }
}
