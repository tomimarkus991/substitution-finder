package com.asendus.api.substitution;

import java.util.Collections;
import java.util.Set;

/** Can a teacher cover period p? Only timetable lessons define the working day; taken substitutions don't extend it. */
public final class Eligibility {
    private Eligibility() {}

    /**
     * @param lessonPeriods periods the teacher teaches that weekday
     * @param blocked       absent in p, already substituting in p, or a placeholder
     */
    public static Availability of(Set<Integer> lessonPeriods, int period, boolean blocked) {
        if (blocked || lessonPeriods.isEmpty() || lessonPeriods.contains(period)) return Availability.HIDDEN;
        int first = Collections.min(lessonPeriods);
        int last = Collections.max(lessonPeriods);
        if ((period > first && period < last) || period == last + 1) return Availability.FREE;
        if (period == first - 1) return Availability.ASK_FIRST;
        return Availability.HIDDEN;
    }
}
