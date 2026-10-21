package com.pintask.app;

import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.*;

/** Explicit, offline extraction of a single date/time from user-shared message text.
 * Never creates or schedules tasks; an editable review is mandatory.
 */
final class MessageTaskParser {
    private static final Pattern LONG_DATE=Pattern.compile(
        "(?iu)(?<![\\p{L}\\d])(?:(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\s*,?\\s+)?(\\d{1,2})\\.?\\s+([\\p{L}]{3,20})\\s+(\\d{4})(?!\\d)");
    private static final Pattern MONTH_FIRST=Pattern.compile(
        "(?iu)(?<![\\p{L}\\d])([\\p{L}]{3,20})\\s+(\\d{1,2}),?\\s+(\\d{4})(?!\\d)");
    private static final Pattern ISO_DATE=Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final Pattern NUMERIC_DATE=Pattern.compile("(?<!\\d)(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})(?!\\d)");
    private static final Pattern TIME=Pattern.compile(
        "(?iu)(?<!\\d)(\\d{1,2}):(\\d{2})\\s*(AM|PM)?(?![\\p{L}\\d])");
    private static final Pattern TIME_ZONE=Pattern.compile(
        "(?i)\\b(CEST|CET|UTC|GMT|BST|PST|PDT|EST|EDT)\\b");
    private static final Pattern IMPORT_META=Pattern.compile(
        "(?im)^\\s*(?:#+\\s*)?(?:Your local time in Germany.*|Local time(?:\\s+in\\s+Germany)?.*|Germany\\s*\\(\\s*(?:CET|CEST)\\s*\\).*)$");

    private MessageTaskParser(){}

    static SpokenTask parse(String original, ZonedDateTime now) {
        if(original==null||original.trim().isEmpty())
            throw new IllegalArgumentException("Paste or share a message with a date and time.");
        if(original.length()>5000)
            throw new IllegalArgumentException("Please shorten the message to 5,000 characters.");
        if(now==null)throw new IllegalArgumentException("Missing current time.");
        String input=original.replace('\u00a0',' ');
        ArrayList<LocalDate> dates=new ArrayList<>();
        collectLongDates(input,dates);
        Matcher monthFirst=MONTH_FIRST.matcher(input);
        while(monthFirst.find()) {
            Month month=month(monthFirst.group(1));
            if(month!=null)addDate(dates,LocalDate.of(Integer.parseInt(monthFirst.group(3)),month,
                    Integer.parseInt(monthFirst.group(2))));
        }
        Matcher iso=ISO_DATE.matcher(input);
        while(iso.find()) addDate(dates,LocalDate.of(
                Integer.parseInt(iso.group(1)),Integer.parseInt(iso.group(2)),Integer.parseInt(iso.group(3))));
        Matcher numeric=NUMERIC_DATE.matcher(input);
        while(numeric.find())addDate(dates,LocalDate.of(
                Integer.parseInt(numeric.group(3)),Integer.parseInt(numeric.group(2)),Integer.parseInt(numeric.group(1))));
        if(dates.isEmpty())throw new IllegalArgumentException(
                "No full date found. Include the day, month and year (for example 23 October 2026).");
        if(dates.size()!=1)throw new IllegalArgumentException(
                "The message contains multiple dates. Keep only the date you want to use.");

        ArrayList<LocalTime> times=new ArrayList<>();
        Matcher time=TIME.matcher(input);
        while(time.find()) {
            int hour=Integer.parseInt(time.group(1));
            int minute=Integer.parseInt(time.group(2));
            String ampm=time.group(3);
            if(minute>59||hour>23||(ampm!=null&&(hour<1||hour>12)))
                throw new IllegalArgumentException("Invalid clock time in message. Edit it before continuing.");
            if(ampm!=null) {
                hour=hour%12;
                if(ampm.equalsIgnoreCase("PM"))hour+=12;
            }
            LocalTime parsed=LocalTime.of(hour,minute);
            if(!times.contains(parsed))times.add(parsed);
        }
        if(times.isEmpty())throw new IllegalArgumentException(
                "No clock time found. Include HH:mm, such as 03:30 AM or 15:30.");
        if(times.size()!=1)throw new IllegalArgumentException(
                "The message contains multiple times. Keep only the time you want to use.");

        Matcher tz=TIME_ZONE.matcher(input);
        String zoneLabel=null;
        while(tz.find()) {
            String found=tz.group(1).toUpperCase(Locale.ROOT);
            if(zoneLabel!=null&&!zoneLabel.equals(found))
                throw new IllegalArgumentException("Multiple time zones found. Specify one time zone.");
            zoneLabel=found;
        }
        ZoneId zone=now.getZone();
        if(zoneLabel!=null) {
            if(!zoneLabel.equals("CET")&&!zoneLabel.equals("CEST"))
                throw new IllegalArgumentException(
                        "Only CET/CEST time-zone labels are supported for imported messages. Convert other times to your local time first.");
            zone=ZoneId.of("Europe/Berlin");
        } else if(input.toLowerCase(Locale.ROOT).contains("local time in germany")) {
            zone=ZoneId.of("Europe/Berlin");
        }

        LocalDate date=dates.get(0);
        LocalTime clock=times.get(0);
        LocalDateTime dateTime=LocalDateTime.of(date,clock);
        if(zone.getRules().getValidOffsets(dateTime).size()!=1)
            throw new IllegalArgumentException(
                    "This clock time is missing or occurs twice on the daylight-saving change. Choose an unambiguous time.");
        ZonedDateTime due=dateTime.atZone(zone);
        if(zoneLabel!=null) {
            String actual=DateTimeFormatter.ofPattern("zzz",Locale.ENGLISH).format(due);
            if(!actual.equalsIgnoreCase(zoneLabel))
                throw new IllegalArgumentException(
                        "The message's "+zoneLabel+" label doesn't match the date in Germany ("+actual+").");
        }

        String proposed=title(input);
        return SpokenTask.imported(proposed,due.toInstant().toEpochMilli(),original.trim());
    }

    private static void collectLongDates(String input,List<LocalDate> dates) {
        Matcher m=LONG_DATE.matcher(input);
        while(m.find()) {
            Month mo=month(m.group(3));
            if(mo==null)continue;
            LocalDate date=LocalDate.of(Integer.parseInt(m.group(4)),mo,Integer.parseInt(m.group(2)));
            if(m.group(1)!=null) {
                DayOfWeek actual=date.getDayOfWeek();
                if(!actual.name().equalsIgnoreCase(m.group(1)))
                    throw new IllegalArgumentException(
                            "The weekday and date disagree (the given date is "+actual+"). Check the message.");
            }
            addDate(dates,date);
        }
    }
    private static void addDate(List<LocalDate> dates,LocalDate found) {
        if(!dates.contains(found))dates.add(found);
    }
    private static Month month(String name) {
        for(Locale locale:new Locale[]{Locale.ENGLISH,Locale.GERMAN,new Locale("hr","HR")}) {
            for(String pattern:new String[]{"MMMM","MMM"}) {
                try {
                    Month m=new DateTimeFormatterBuilder().parseCaseInsensitive()
                            .appendPattern(pattern).toFormatter(locale)
                            .parse(name,Month::from);
                    return m;
                } catch(DateTimeException ignored) {}
            }
        }
        return null;
    }
    private static String title(String input) {
        String candidate=IMPORT_META.matcher(input).replaceAll(" ");
        candidate=LONG_DATE.matcher(candidate).replaceAll(" ");
        candidate=MONTH_FIRST.matcher(candidate).replaceAll(" ");
        candidate=ISO_DATE.matcher(candidate).replaceAll(" ");
        candidate=NUMERIC_DATE.matcher(candidate).replaceAll(" ");
        candidate=TIME.matcher(candidate).replaceAll(" ");
        candidate=TIME_ZONE.matcher(candidate).replaceAll(" ");
        candidate=candidate.replaceAll("(?i)\\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b", " ");
        candidate=candidate.replaceAll("(?i)\\b(?:your\\s+local\\s+time|in\\s+Germany|at|on|am|pm)\\b"," ");
        candidate=candidate.replaceAll("[#*()\\[\\]]"," ");
        for(String line:candidate.split("\\R")) {
            String s=line.replaceAll("^[^\\p{L}\\d]+|[^\\p{L}\\d]+$","").trim();
            if(s.length()>2 && s.length()<=160 && !s.startsWith("http") &&
                    !s.equalsIgnoreCase("Your local time in Germany"))return s;
        }
        // The example is just a date/time, not a task name: leave it blank and require input.
        return "";
    }
}
