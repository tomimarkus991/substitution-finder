package com.asendus.api.duty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DutyNamesTest {
    @Test void plainList() {
        assertThat(DutyNames.surnames("Kask, Tamm")).containsExactly("Kask", "Tamm");
    }
    @Test void canteenPrefix() {
        assertThat(DutyNames.isCanteen("sööklas: Mänd, Saar")).isTrue();
        assertThat(DutyNames.surnames("sööklas: Mänd, Saar")).containsExactly("Mänd", "Saar");
        assertThat(DutyNames.isCanteen("Kask")).isFalse();
    }
    @Test void cutsAtDoubleSlash() {
        assertThat(DutyNames.surnames("Lepp // 4.s klj tund (315)")).containsExactly("Lepp");
        assertThat(DutyNames.surnames("sööklas: Lepp, Pihlakas // 6.S ÕVT (12:35 -13:10) - pos 4 (Kask/Kuusk)"))
                .containsExactly("Lepp", "Pihlakas");
    }
    @Test void dropsParentheses() {
        assertThat(DutyNames.surnames("Kuusk (õvt +6.S)")).containsExactly("Kuusk");
        assertThat(DutyNames.surnames("Saar ")).containsExactly("Saar");
    }
    @Test void takesWhatFollowsDash() {
        assertThat(DutyNames.surnames("4.S & 6.R ÕVT - Vaher")).containsExactly("Vaher");
    }
    @Test void splitsOnAmpersandAndSlash() {
        assertThat(DutyNames.surnames("Tamm & Mänd/Vaher")).containsExactly("Tamm", "Mänd", "Vaher");
    }
    @Test void hiddenDutyIsDetected() {
        assertThat(DutyNames.hidesDuty("sööklas: Lepp, Pihlakas // 6.S ÕVT (12:35 -13:10) - pos 4 (Kask/Kuusk)")).isTrue();
        assertThat(DutyNames.hidesDuty("Kuusk (õvt +6.S)")).isTrue();
        assertThat(DutyNames.hidesDuty("Lepp // 4.s klj tund (315)")).isFalse();
        assertThat(DutyNames.hidesDuty("Kask, Tamm")).isFalse();
    }
    @Test void labels() {
        Duty d = Duty.builder().kind("OVT").starts(LocalTime.of(10, 45)).ends(LocalTime.of(11, 15)).position("4").build();
        assertThat(DutyLabels.label(d)).isEqualTo("ÕVT 10:45–11:15");
        assertThat(DutyLabels.labelWithPosition(d)).isEqualTo("ÕVT 10:45–11:15 · pos 4");
        d.setKind("SVT"); d.setPosition(""); d.setStarts(LocalTime.of(9, 5)); d.setEnds(LocalTime.of(9, 50));
        assertThat(DutyLabels.labelWithPosition(d)).isEqualTo("SVT 9:05–9:50");
    }
}
