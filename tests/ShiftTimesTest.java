package com.pintask.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

public final class ShiftTimesTest {
    private static void assertTime(String expected, long epoch, ZoneId zone) {
        String actual = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static long t(String timestamp, ZoneId zone) {
        return LocalDateTime.parse(timestamp).atZone(zone).toInstant().toEpochMilli();
    }
    public static void main(String[] args) {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        long night = t("2026-10-14T20:00:00", berlin);
        assertTime("2026-10-14 20:00", ShiftTimes.atMinuteOffset(night, 0, berlin), berlin);
        assertTime("2026-10-14 21:00", ShiftTimes.atMinuteOffset(night, 60, berlin), berlin);
        assertTime("2026-10-15 02:00", ShiftTimes.atMinuteOffset(night, 360, berlin), berlin);
        assertTime("2026-10-15 08:00", ShiftTimes.atMinuteOffset(night, 720, berlin), berlin);
        long day = t("2026-10-14T08:00:00", berlin);
        assertTime("2026-10-14 17:00", ShiftTimes.atMinuteOffset(day, 9*60, berlin), berlin);
        // DST fall-back: ends at 08:00 local rather than incorrectly at 07:00.
        long change = t("2026-10-24T20:00:00", berlin);
        assertTime("2026-10-25 08:00", ShiftTimes.atMinuteOffset(change, 720, berlin), berlin);
        if (ShiftTimes.occurrenceOffsets(0, 60, 720).size() != 12)
            throw new AssertionError("Night shift should generate 12 hourly events");
        if (ShiftTimes.occurrenceOffsets(0, 60, 720).get(11) != 660)
            throw new AssertionError("Last hourly task should be 07:00 for a 20:00 start");
        if (ShiftTimes.occurrenceOffsets(120, 0, 720).size() != 1)
            throw new AssertionError("One-time rule should generate 1 event");
        if (ShiftTimes.occurrenceOffsets(600, 120, 720).size() != 1)
            throw new AssertionError("Interval must stop at shift end");
        System.out.println("ShiftTimes tests passed (hourly, once, bounds, DST)");
    }
}
