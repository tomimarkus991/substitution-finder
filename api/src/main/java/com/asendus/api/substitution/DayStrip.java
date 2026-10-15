package com.asendus.api.substitution;

import com.asendus.api.duty.Duty;
import com.asendus.api.duty.DutyLabels;
import com.asendus.api.substitution.DayDtos.DaySlot;
import com.asendus.api.timetable.Lesson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A teacher's day as one slot per period, and how many periods in a row they'd work. */
public final class DayStrip {
    public static final int PERIODS = 7;
    private static final String JOIN = "; ";

    private DayStrip() {}

    /** Lessons beat substitutions, which beat break duties; several of one kind in a period are joined with "; ". */
    public static List<DaySlot> of(List<Lesson> lessons, List<Substitution> substituting, List<Duty> duties) {
        List<DaySlot> out = new ArrayList<>(PERIODS);
        for (int p = 1; p <= PERIODS; p++) {
            int period = p;
            String lesson = joined(lessons.stream().filter(l -> l.getPeriod() == period)
                    .map(l -> label(l.getClasses(), l.getSubject())));
            String sub = joined(substituting.stream().filter(s -> s.getPeriod() == period)
                    .map(s -> s.getDutyLabel() != null ? s.getDutyLabel() : label(s.getClasses(), s.getSubject())));
            String duty = joined(duties.stream().filter(d -> d.getPeriod() == period).map(DutyLabels::label));
            if (lesson != null) out.add(new DaySlot(p, SlotKind.LESSON, lesson));
            else if (sub != null) out.add(new DaySlot(p, SlotKind.SUBSTITUTING, sub));
            else if (duty != null) out.add(new DaySlot(p, SlotKind.DUTY, duty));
            else out.add(new DaySlot(p, SlotKind.EMPTY, ""));
        }
        return out;
    }

    /** Length of the run of lessons and substitutions that contains the period, counting the period itself. */
    public static int streak(List<DaySlot> day, int period) {
        int from = period;
        int to = period;
        while (from > 1 && busy(day.get(from - 2))) from--;
        while (to < PERIODS && busy(day.get(to))) to++;
        return to - from + 1;
    }

    /** "4R · Ek"; blank parts are left out. */
    public static String label(String classes, String subject) {
        return Stream.of(classes, subject).filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" · "));
    }

    private static boolean busy(DaySlot s) {
        return s.kind() == SlotKind.LESSON || s.kind() == SlotKind.SUBSTITUTING;
    }

    private static String joined(Stream<String> labels) {
        List<String> list = labels.toList();
        return list.isEmpty() ? null : String.join(JOIN, list);
    }
}
