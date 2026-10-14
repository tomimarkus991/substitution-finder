package com.asendus.api.timetable;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherKeyTest {

    @Test
    void normalizesCaseWhitespaceAndUnicodeForm() {
        String decomposed = "Pärnpuu  Taimi ";   // ä as a + combining diaeresis
        assertThat(TeacherKey.of(decomposed)).isEqualTo("pärnpuu taimi");
        assertThat(TeacherKey.of("Illak Pille- Riin")).isEqualTo("illak pille- riin");
    }

    @Test
    void displayNamePutsFirstNameFirst() {
        assertThat(TeacherKey.displayName("Pärnpuu Taimi")).isEqualTo("Taimi Pärnpuu");
        assertThat(TeacherKey.displayName("Undusk Marlen Elisabeth")).isEqualTo("Marlen Elisabeth Undusk");
        assertThat(TeacherKey.displayName("Puutöö")).isEqualTo("Puutöö");
    }
}
