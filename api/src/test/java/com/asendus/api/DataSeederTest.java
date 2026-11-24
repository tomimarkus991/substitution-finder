package com.asendus.api;

import com.asendus.api.duty.DutyService;
import com.asendus.api.service.AuthService;
import com.asendus.api.substitution.DayDtos.AbsenceDto;
import com.asendus.api.substitution.DayDtos.SubstitutionDto;
import com.asendus.api.substitution.SubstitutionService;
import com.asendus.api.timetable.TimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataSeederTest extends IntegrationTest {

    static final LocalDate FRI = LocalDate.of(2026, 9, 4);
    @Autowired JdbcTemplate jdbc;
    @Autowired SubstitutionService substitutionService;
    @Autowired TimetableService timetableService;
    @Autowired DutyService dutyService;
    DataSeeder seeder;
    long userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, teacher_name_override, absence, duty_version CASCADE");
        seeder = new DataSeeder(mock(AuthService.class), substitutionService, timetableService, dutyService);
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

    /** Surnames of two Friday teachers with lessons from period 3 on, each surname unique in the timetable. */
    List<String> fridaySurnames() {
        return jdbc.queryForList("""
                select split_part(t.name, ' ', 1) s from teacher t
                join timetable_version v on v.id = t.version_id and v.active
                join lesson l on l.teacher_id = t.id and l.weekday = 5 and l.period >= 3
                group by t.name
                having (select count(*) from teacher t2 where t2.version_id = min(t.version_id)
                        and split_part(t2.name, ' ', 1) = split_part(t.name, ' ', 1)) = 1
                order by count(*) desc, t.name limit 2""", String.class);
    }

    @Test
    void anAbsentTeachersDutyIsSeededAndFilled() throws Exception {
        uploadTimetable();
        List<String> surnames = fridaySurnames();
        byte[] xlsx = new TestDutySheet().day("R")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", surnames.get(0), 5, surnames.get(1), 4)
                .bytes();
        mockMvc.perform(multipart("/duties").file(new MockMultipartFile("file", "valved.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                .header("Authorization", bearer())).andExpect(status().isCreated());

        seeder.seedDay(FRI, userId);

        List<SubstitutionDto> dutyRows = substitutionService.day(FRI).absences().stream()
                .flatMap(a -> a.rows().stream()).filter(r -> r.dutyLabel() != null).toList();
        assertThat(dutyRows).isNotEmpty();
        assertThat(dutyRows).filteredOn(r -> !r.substituteKeys().isEmpty()).isNotEmpty();
    }

    @Test
    void seedsTheLastSchoolDaysUpToTodaySkippingWeekends() throws Exception {
        uploadTimetable();
        LocalDate monday = LocalDate.of(2026, 9, 7);
        seeder.seedRecent(monday, 3, userId);

        assertThat(jdbc.queryForList("select distinct date from absence order by date", LocalDate.class))
                .containsExactly(LocalDate.of(2026, 9, 3), FRI, monday);
    }

    @Test
    void aWeekendSeedsTheNextMonday() {
        assertThat(DataSeeder.schoolDay(LocalDate.of(2026, 9, 5))).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(DataSeeder.schoolDay(LocalDate.of(2026, 9, 6))).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(DataSeeder.schoolDay(FRI)).isEqualTo(FRI);
    }
}
