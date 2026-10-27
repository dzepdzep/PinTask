package com.pintask.app;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** User-like speech, including two clauses, fillers and loose punctuation. */
public class ConversationalVoiceRegressionTest {
    private static final ZonedDateTime NOW=ZonedDateTime.of(
            2026,11,10,14,10,0,0,ZoneId.of("Europe/Berlin"));

    private static void one(String lang,String speech,String title,int minutesDue,int minutesBefore){
        SpokenTask actual=VoiceLanguages.parse(speech,NOW,lang);
        assertEquals(speech,title,actual.title);
        assertEquals(speech,NOW.plusMinutes(minutesDue).toInstant().toEpochMilli(),actual.dueAt);
        assertEquals(speech,minutesBefore,actual.remindBeforeMinutes);
        assertEquals(speech,speech,actual.original);
        assertEquals(speech,0,actual.repeatCode);
    }
    private static void days(String lang,String speech,String title,int days,int minutesBefore){
        SpokenTask actual=VoiceLanguages.parse(speech,NOW,lang);
        assertEquals(speech,title,actual.title);
        assertEquals(speech,NOW.plusDays(days).toInstant().toEpochMilli(),actual.dueAt);
        assertEquals(speech,minutesBefore,actual.remindBeforeMinutes);
    }

    @Test public void englishInformalOneSentenceAndFollowUpClauses(){
        one("en-US","Remind me to switch the computer off in an hour and remind me ten minutes earlier",
                "switch the computer off",60,10);
        days("en-US","Take the rubbish out in two days, and remind me 15 minutes before",
                "Take the rubbish out",2,15);
        days("en-US","take the trash out into days but remind me fifteen minutes earlier",
                "take the trash out",2,15);
        one("en-US","Remind me in 45 minutes to drink water, please remind me 10 minutes before",
                "drink water",45,10);
        days("en-US","remind me to take out the bins in two days 15 minutes earlier",
                "take out the bins",2,15);
    }

    @Test public void germanCasualAndFormalSentenceOrder(){
        days("de-DE","Erinnere mich in zwei Tagen daran, den Müll rauszubringen und erinnere mich 15 Minuten vorher",
                "den Müll rauszubringen",2,15);
        days("de-DE","Erinner mich in zwei Tagen dran, den Müll rauszubringen, und erinner mich fünf Minuten vorher",
                "den Müll rauszubringen",2,5);
        days("de-DE","Müll rausbringen in zwei Tagen, zehn Minuten vorher",
                "Müll rausbringen",2,10);
        one("de-DE","Erinnere mich in einer Stunde daran, den PC auszuschalten und erinnere mich fünf Minuten früher",
                "den PC auszuschalten",60,5);
        one("de-DE","Erinnere mich daran, die Waschmaschine auszuräumen in 30 Minuten, 5 Minuten vorher",
                "die Waschmaschine auszuräumen",30,5);
    }

    @Test public void croatianCasualAndCompoundVoicePhrases(){
        days("hr-HR","Podsjeti me za dva dana da iznesem smeće i podsjeti me petnaest minuta ranije",
                "iznesem smeće",2,15);
        one("hr-HR","Podsjeti me da kupim mlijeko za dva sata, barem deset minuta ranije",
                "kupim mlijeko",120,10);
        one("hr-HR","Trebam ugasiti kompjuter za 30 minuta, podsjeti me pet minuta prije",
                "ugasiti kompjuter",30,5);
        days("hr-HR","Podsjeti me za dva dana da ugasim lampu, ali podsjeti me pet minuta ranije",
                "ugasim lampu",2,5);
    }

    @Test public void malformedTimingMustFailRatherThanGuess(){
        for(String[] invalid:new String[][]{
                {"en-US","remind me to send the email in two days and in three days"},
                {"en-US","remind me to call in 10 minutes and remind me 15 minutes earlier"},
                {"en-US","remind me to go for a walk 10 minutes earlier"},
                {"de-DE","Erinnere mich in zwei Tagen daran, den Müll rauszubringen, fünf Minuten vorher und zehn Minuten vorher"},
                {"hr-HR","Podsjeti me za pet minuta da nazovem, deset minuta ranije"}
        }){
            assertThrows(invalid[1],IllegalArgumentException.class,
                    ()->VoiceLanguages.parse(invalid[1],NOW,invalid[0]));
        }
    }
}
