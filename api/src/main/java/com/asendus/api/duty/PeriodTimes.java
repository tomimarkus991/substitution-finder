package com.asendus.api.duty;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** The school's bell schedule, as printed in the aSc timetable PDF. */
public final class PeriodTimes {
    private PeriodTimes() {}

    private record Slot(LocalTime start, LocalTime end) {}

    private static final List<Slot> SLOTS = List.of(
            slot("8:45", "9:30"), slot("9:40", "10:25"), slot("10:35", "11:20"), slot("11:30", "12:15"),
            slot("12:25", "13:10"), slot("13:20", "14:05"), slot("14:15", "15:00"));

    /**
     * The period a break duty belongs to: the one it overlaps most (ties → the earlier one), or 0 if none.
     * The break fills the teacher's empty slot in the timetable, so one period is enough.
     */
    public static int periodOf(LocalTime start, LocalTime end) {
        int best = 0;
        long bestMinutes = 0;
        for (int i = 0; i < SLOTS.size(); i++) {
            Slot s = SLOTS.get(i);
            LocalTime from = start.isAfter(s.start()) ? start : s.start();
            LocalTime to = end.isBefore(s.end()) ? end : s.end();
            long minutes = Duration.between(from, to).toMinutes();
            if (minutes > bestMinutes) {
                best = i + 1;
                bestMinutes = minutes;
            }
        }
        return best;
    }

    /** "8:45" → 08:45. */
    static LocalTime parse(String hm) {
        String[] p = hm.split(":");
        return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    private static Slot slot(String start, String end) {
        return new Slot(parse(start), parse(end));
    }
}
