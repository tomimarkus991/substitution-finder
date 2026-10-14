package com.asendus.api.duty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PeriodTimesTest {
    static int p(String start, String end) {
        return PeriodTimes.periodOf(PeriodTimes.parse(start), PeriodTimes.parse(end));
    }

    @Test void outdoorBreaksFallInTheirLessonSlot() {
        assertThat(p("10:45", "11:15")).isEqualTo(3);
        assertThat(p("11:05", "11:30")).isEqualTo(3);
        assertThat(p("10:25", "11:05")).isEqualTo(3);  // touching 2nd period's end doesn't count
        assertThat(p("12:55", "13:20")).isEqualTo(5);
        assertThat(p("12:35", "13:10")).isEqualTo(5);
    }

    @Test void canteenDutyGoesToThePeriodItOverlapsMost() {
        assertThat(p("12:10", "12:55")).isEqualTo(5);   // 5 min of 4th, 30 min of 5th
    }

    @Test void tieGoesToTheEarlierPeriod() {
        assertThat(p("11:10", "11:40")).isEqualTo(3);   // 10 min each
    }

    @Test void outsideTheDayIsZero() {
        assertThat(p("15:10", "15:40")).isZero();
        assertThat(p("12:15", "12:25")).isZero();       // exactly the break between 4th and 5th
    }
}
