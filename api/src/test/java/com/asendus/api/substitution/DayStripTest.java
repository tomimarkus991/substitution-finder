package com.asendus.api.substitution;

import com.asendus.api.duty.Duty;
import com.asendus.api.substitution.DayDtos.DaySlot;
import com.asendus.api.timetable.Lesson;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static com.asendus.api.substitution.SlotKind.*;
import static org.assertj.core.api.Assertions.assertThat;

class DayStripTest {

    static Lesson lesson(int period, String classes, String subject) {
        return Lesson.builder().period((short) period).classes(classes).subject(subject).room("").note("").build();
    }

    static Substitution sub(int period, String classes, String subject, String dutyLabel) {
        return Substitution.builder().period((short) period).classes(classes).subject(subject).dutyLabel(dutyLabel).build();
    }

    static Duty duty(int period) {
        return Duty.builder().period((short) period).kind("OVT")
                .starts(LocalTime.of(10, 45)).ends(LocalTime.of(11, 15)).classGroup("I klassid").rawName("X").build();
    }

    @Test void sevenSlotsWithKindsAndLabels() {
        List<DaySlot> day = DayStrip.of(
                List.of(lesson(1, "4R", "Ek"), lesson(2, "5D", "Lo")),
                List.of(sub(4, "6R", "Aj", null)),
                List.of(duty(5)));
        assertThat(day).extracting(DaySlot::period).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(day).extracting(DaySlot::kind).containsExactly(LESSON, LESSON, EMPTY, SUBSTITUTING, DUTY, EMPTY, EMPTY);
        assertThat(day.get(0).label()).isEqualTo("4R · Ek");
        assertThat(day.get(2).label()).isEmpty();
        assertThat(day.get(3).label()).isEqualTo("6R · Aj");
        assertThat(day.get(4).label()).isEqualTo("ÕVT 10:45–11:15");
    }

    @Test void lessonBeatsSubstitutionBeatsDuty() {
        List<DaySlot> day = DayStrip.of(List.of(lesson(3, "7A", "Ma")),
                List.of(sub(3, "8B", "Ek", null), sub(4, "8B", "Ek", null)), List.of(duty(3), duty(4)));
        assertThat(day.get(2).kind()).isEqualTo(LESSON);
        assertThat(day.get(3).kind()).isEqualTo(SUBSTITUTING);
    }

    @Test void severalInOnePeriodAreJoined() {
        List<DaySlot> day = DayStrip.of(List.of(lesson(1, "6S", "KKt"), lesson(1, "3.1", "ujumine")),
                List.of(sub(2, "I klassid", "ÕVT", "ÕVT 10:45–11:15 · pos 4")), List.of());
        assertThat(day.get(0).label()).isEqualTo("6S · KKt; 3.1 · ujumine");
        assertThat(day.get(1).label()).isEqualTo("ÕVT 10:45–11:15 · pos 4"); // a duty row shows its duty label
    }

    @Test void blankPartsAreLeftOut() {
        assertThat(DayStrip.label("4R", "")).isEqualTo("4R");
        assertThat(DayStrip.label("", "Ek")).isEqualTo("Ek");
    }

    @Test void streakCountsLessonsAndSubstitutionsAroundThePeriod() {
        // lessons 1,2,4,5; substituting 6; duty 7; asking about 3 → 1..6
        List<DaySlot> day = DayStrip.of(
                List.of(lesson(1, "a", "x"), lesson(2, "a", "x"), lesson(4, "a", "x"), lesson(5, "a", "x")),
                List.of(sub(6, "b", "y", null)), List.of(duty(7)));
        assertThat(DayStrip.streak(day, 3)).isEqualTo(6);
    }

    @Test void streakAtTheEdgesAndAlone() {
        List<DaySlot> day = DayStrip.of(List.of(lesson(2, "a", "x"), lesson(6, "a", "x")), List.of(), List.of());
        assertThat(DayStrip.streak(day, 1)).isEqualTo(2);
        assertThat(DayStrip.streak(day, 7)).isEqualTo(2);
        assertThat(DayStrip.streak(day, 4)).isEqualTo(1);
    }
}
