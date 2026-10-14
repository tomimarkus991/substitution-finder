package com.asendus.api.substitution;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.asendus.api.substitution.Availability.*;
import static org.assertj.core.api.Assertions.assertThat;

class EligibilityTest {

    // lessons in periods 2, 3, 5
    static final Set<Integer> DAY = Set.of(2, 3, 5);

    static Availability check(int period) {
        return Eligibility.of(DAY, period, false);
    }

    @Test
    void gapInsideTheDayIsFree() { assertThat(check(4)).isEqualTo(FREE); }

    @Test
    void periodRightAfterTheLastLessonIsFree() { assertThat(check(6)).isEqualTo(FREE); }

    @Test
    void twoPeriodsAfterTheLastLessonIsHidden() { assertThat(check(7)).isEqualTo(HIDDEN); }

    @Test
    void periodRightBeforeTheFirstLessonNeedsAsking() { assertThat(check(1)).isEqualTo(ASK_FIRST); }

    @Test
    void twoPeriodsBeforeTheFirstLessonIsHidden() {
        assertThat(Eligibility.of(Set.of(3, 4), 1, false)).isEqualTo(HIDDEN);
    }

    @Test
    void teachingThatPeriodIsHidden() { assertThat(check(3)).isEqualTo(HIDDEN); }

    @Test
    void noLessonsThatDayIsHidden() { assertThat(Eligibility.of(Set.of(), 3, false)).isEqualTo(HIDDEN); }

    @Test
    void blockedIsHidden() { assertThat(Eligibility.of(DAY, 4, true)).isEqualTo(HIDDEN); }

    @Test
    void singleLessonDay() {
        Set<Integer> single = Set.of(3);
        assertThat(Eligibility.of(single, 2, false)).isEqualTo(ASK_FIRST);
        assertThat(Eligibility.of(single, 4, false)).isEqualTo(FREE);
        assertThat(Eligibility.of(single, 1, false)).isEqualTo(HIDDEN);
        assertThat(Eligibility.of(single, 5, false)).isEqualTo(HIDDEN);
    }
}
