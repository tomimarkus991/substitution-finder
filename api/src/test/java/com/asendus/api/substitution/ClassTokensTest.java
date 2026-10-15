package com.asendus.api.substitution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassTokensTest {
    @Test void splitsOnSlashesAndIgnoresEmptyParts() {
        assertThat(ClassTokens.of("2.3/2.4/2.5/2.3V/")).containsExactlyInAnyOrder("2.3", "2.4", "2.5", "2.3v");
        assertThat(ClassTokens.of("")).isEmpty();
    }

    @Test void overlapIsExactAndCaseInsensitive() {
        assertThat(ClassTokens.overlap("7A/7B", "7b")).isTrue();
        assertThat(ClassTokens.overlap("2.3V", "2.3")).isFalse();
        assertThat(ClassTokens.overlap("/", "/")).isFalse();
        assertThat(ClassTokens.overlap("I klassid", "4R")).isFalse();
    }
}
