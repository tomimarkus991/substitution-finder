package com.asendus.api;

import com.asendus.api.service.AuthService;
import com.asendus.api.substitution.DayDtos.AbsenceDto;
import com.asendus.api.substitution.DayDtos.SubstitutionDto;
import com.asendus.api.substitution.SubstitutionService;
import com.asendus.api.timetable.TimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataSeederTest extends IntegrationTest {

    static final LocalDate FRI = LocalDate.of(2026, 9, 4);
    @Autowired JdbcTemplate jdbc;
    @Autowired SubstitutionService substitutionService;
    @Autowired TimetableService timetableService;
    DataSeeder seeder;
    long userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, teacher_name_override, absence, duty_version CASCADE");
        seeder = new DataSeeder(mock(AuthService.class), substitutionService, timetableService);
        bearer(); // creates the test user
        userId = jdbc.queryForObject("select id from app_user where google_sub = 'test-user'", Long.class);
    }

    @Test
    void seedsAPartlyFilledAndAnEmptyAbsence() throws Exception {
        uploadTimetable();
        seeder.seedDay(FRI, userId);

        List<AbsenceDto> absences = substitutionService.day(FRI).absences();
        assertThat(absences).hasSize(2);
        assertThat(absences).extracting(AbsenceDto::teacherKey).doesNotHaveDuplicates();
        AbsenceDto partial = absences.stream().filter(a -> a.fromPeriod() == 3).findFirst().orElseThrow();
        assertThat(partial.toPeriod()).isEqualTo(7);
        assertThat(partial.rows()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(partial.rows()).filteredOn(r -> !r.substituteKeys().isEmpty()).isNotEmpty();
        assertThat(partial.rows()).filteredOn(r -> r.substituteKeys().isEmpty()).isNotEmpty();
        AbsenceDto whole = absences.stream().filter(a -> a.fromPeriod() == 1).findFirst().orElseThrow();
        assertThat(whole.rows()).isNotEmpty().allMatch(r -> r.substituteKeys().isEmpty());
        assertThat(partial.rows()).extracting(SubstitutionDto::substituteKeys)
                .allMatch(keys -> !keys.contains(whole.teacherKey()));
    }

    @Test
    void leavesADayWithAbsencesAlone() throws Exception {
        uploadTimetable();
        seeder.seedDay(FRI, userId);
        seeder.seedDay(FRI, userId);
        assertThat(substitutionService.day(FRI).absences()).hasSize(2);
    }

    @Test
    void doesNothingWithoutATimetable() {
        seeder.seedDay(FRI, userId);
        assertThat(substitutionService.day(FRI).absences()).isEmpty();
    }

    @Test
    void aWeekendSeedsTheNextMonday() {
        assertThat(DataSeeder.schoolDay(LocalDate.of(2026, 9, 5))).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(DataSeeder.schoolDay(LocalDate.of(2026, 9, 6))).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(DataSeeder.schoolDay(FRI)).isEqualTo(FRI);
    }
}
