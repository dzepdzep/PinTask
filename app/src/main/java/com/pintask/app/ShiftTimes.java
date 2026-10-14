package com.pintask.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Computes shift times in local wall-clock time, including overnight/DST transitions. */
public final class ShiftTimes {
    private ShiftTimes() {}

    public static long atMinuteOffset(long startMillis, int minuteOffset, ZoneId zone) {
        LocalDateTime shiftStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zone);
        return shiftStart.plusMinutes(minuteOffset).atZone(zone).toInstant().toEpochMilli();
    }

    /** A fixed rule yields one item; recurring rules stop strictly before the shift end. */
    public static List<Integer> occurrenceOffsets(int firstMinute, int everyMinute, int durationMinutes) {
        if (durationMinutes <= 0 || durationMinutes > 1440 || firstMinute < 0 ||
                firstMinute >= durationMinutes || everyMinute < 0)
            throw new IllegalArgumentException("Invalid shift rule");
        List<Integer> result = new ArrayList<>();
        for (int m = firstMinute; m < durationMinutes; m += everyMinute > 0 ? everyMinute : durationMinutes) {
            result.add(m);
            if (result.size() > 120) throw new IllegalArgumentException("Too many occurrences");
            if (everyMinute == 0) break;
        }
        return result;
    }

    public static long atMinuteOffset(long startMillis, int minuteOffset) {
        return atMinuteOffset(startMillis, minuteOffset, ZoneId.systemDefault());
    }
}
