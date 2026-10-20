package com.pintask.app;

import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VoiceReviewModelTest {
    private static final ZoneId BERLIN=ZoneId.of("Europe/Berlin");
    private static final ZonedDateTime NOW=ZonedDateTime.of(2026,10,20,11,0,0,0,BERLIN);

    @Test public void detectedFieldsAreSeparateAndShowWhatWasHeard(){
        SpokenTask heard=SpokenTask.parse("I need to wash the dishes today at 13",NOW);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        assertEquals(LocalDate.of(2026,10,20),draft.day());
        assertEquals(LocalTime.of(13,0),draft.time());
        assertTrue(draft.dateLabel().contains("20 Oct 2026"));
        assertEquals("13:00",draft.timeLabel());
        assertEquals("Does not repeat",draft.repeatLabel());
        assertTrue(draft.preview().contains("13:00"));
        assertEquals(heard.dueAt,draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }

    @Test public void correctTimeWithoutChangingEntireSpokenCommand(){
        SpokenTask heard=SpokenTask.parse("Wash dishes tomorrow at 13",NOW);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        draft.changeTime(LocalTime.of(14,45),NOW.toLocalDate());
        assertEquals(LocalDate.of(2026,10,21),draft.day());
        assertEquals("14:45",draft.timeLabel());
        assertEquals(ZonedDateTime.of(2026,10,21,14,45,0,0,BERLIN).toInstant().toEpochMilli(),
                draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
        assertNotEquals(heard.dueAt,draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }

    @Test public void correctingDateIndependentlyRetainsTimeAndRepeat(){
        SpokenTask heard=SpokenTask.parse("Rinse plates every day at 08:00",NOW);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        draft.changeDate(LocalDate.of(2026,10,22));
        assertEquals("08:00",draft.timeLabel());
        assertEquals(TaskStore.REPEAT_DAILY,draft.repeatCode());
        assertEquals(ZonedDateTime.of(2026,10,22,8,0,0,0,BERLIN).toInstant().toEpochMilli(),
                draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }

    @Test public void everyOtherTuesdayIsVisibleAndCanChangeRepeatIndependently(){
        SpokenTask heard=SpokenTask.parse("Take out bins every other Tuesday at 08:00",NOW);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        assertEquals("Every other Tuesday",draft.repeatLabel());
        draft.changeTime(LocalTime.of(9,15),NOW.toLocalDate());
        assertEquals("Every other Tuesday",draft.repeatLabel());
        draft.changeRepeat(0);
        assertEquals("Does not repeat",draft.repeatLabel());
        assertEquals(0,draft.repeatCode());
    }

    @Test public void wrongWeekdayDateIsRejectedInsteadOfSilentlyShiftingIt(){
        SpokenTask heard=SpokenTask.parse("Recycle every Tuesday at 09:00",NOW);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        draft.changeDate(LocalDate.of(2026,10,21));
        assertThrows(IllegalArgumentException.class,
                ()->draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
        draft.changeDate(LocalDate.of(2026,10,27));
        assertEquals(heard.dueAt,draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }

    @Test public void unscheduledTaskRequiresBothDateAndTimeWhenEdited(){
        SpokenTask heard=SpokenTask.parse("Remind me to buy milk",NOW);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        assertEquals("Now (pinned task)",draft.timeLabel());
        assertEquals(0,draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
        draft.changeDate(LocalDate.of(2026,10,21));
        assertEquals("Not set · choose a time",draft.timeLabel());
        assertThrows(IllegalArgumentException.class,
                ()->draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
        draft.changeTime(LocalTime.of(18,30),NOW.toLocalDate());
        assertEquals(ZonedDateTime.of(2026,10,21,18,30,0,0,BERLIN).toInstant().toEpochMilli(),
                draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }

    @Test public void originalDelayPreservesSecondsUntilTimeIsChanged(){
        ZonedDateTime now=NOW.plusSeconds(37);
        SpokenTask heard=SpokenTask.parse("Water flowers in 30 minutes",now);
        VoiceReviewModel draft=new VoiceReviewModel(heard,BERLIN);
        assertEquals("11:30:37",draft.timeLabel());
        assertEquals(heard.dueAt,draft.validatedDueAt(now.toInstant().toEpochMilli()));
        draft.changeTime(LocalTime.of(12,45),now.toLocalDate());
        assertEquals("12:45",draft.timeLabel());
        assertEquals(ZonedDateTime.of(2026,10,20,12,45,0,0,BERLIN).toInstant().toEpochMilli(),
                draft.validatedDueAt(now.toInstant().toEpochMilli()));
    }

    @Test public void daylightSavingNonexistentLocalTimeIsRejected(){
        VoiceReviewModel draft=new VoiceReviewModel(
                SpokenTask.parse("Take vitamins tomorrow at 10",NOW),BERLIN);
        draft.changeDate(LocalDate.of(2027,3,28));
        draft.changeTime(LocalTime.of(2,30),NOW.toLocalDate());
        assertThrows(IllegalArgumentException.class,
                ()->draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }

    @Test public void pastEditedTimeAndUntimedRepeatAreRejected(){
        VoiceReviewModel draft=new VoiceReviewModel(SpokenTask.parse("Check mail today at 13:00",NOW),BERLIN);
        draft.changeTime(LocalTime.of(9,30),NOW.toLocalDate());
        assertThrows(IllegalArgumentException.class,
                ()->draft.validatedDueAt(NOW.toInstant().toEpochMilli()));
        VoiceReviewModel other=new VoiceReviewModel(SpokenTask.parse("Buy milk",NOW),BERLIN);
        other.changeRepeat(TaskStore.REPEAT_DAILY);
        assertThrows(IllegalArgumentException.class,
                ()->other.validatedDueAt(NOW.toInstant().toEpochMilli()));
    }
}
