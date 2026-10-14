package com.asendus.api.timetable;

import com.asendus.api.IntegrationTest;
import com.asendus.api.duty.Duty;
import com.asendus.api.duty.DutyRepository;
import com.asendus.api.duty.DutyVersion;
import com.asendus.api.duty.DutyVersionRepository;
import com.asendus.api.model.AppUser;
import com.asendus.api.repository.AppUserRepository;
import com.asendus.api.substitution.Absence;
import com.asendus.api.substitution.AbsenceRepository;
import com.asendus.api.substitution.Substitution;
import com.asendus.api.substitution.SubstitutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the entity mappings against the real schema (ddl-auto validate alone does not). */
class SchemaTest extends IntegrationTest {

    @Autowired AppUserRepository users;
    @Autowired TimetableVersionRepository versions;
    @Autowired TeacherRepository teachers;
    @Autowired LessonRepository lessons;
    @Autowired AbsenceRepository absences;
    @Autowired SubstitutionRepository substitutions;
    @Autowired DutyVersionRepository dutyVersions;
    @Autowired DutyRepository duties;

    @Test
    void entitiesRoundTrip() {
        AppUser user = users.save(AppUser.builder()
                .googleSub("schema-test").email("schema@example.com").name("Schema").build());

        TimetableVersion v = versions.save(TimetableVersion.builder()
                .uploadedBy(user.getId()).fileName("a.pdf").active(false)
                .warnings(List.of("w1", "w2")).build());
        assertThat(versions.findById(v.getId()).orElseThrow().getWarnings()).containsExactly("w1", "w2");
        assertThat(versions.findByActiveFalse()).extracting(TimetableVersion::getId).contains(v.getId());

        Teacher t = teachers.save(Teacher.builder()
                .versionId(v.getId()).teacherKey("tamm_t").name("Tamm Testnimi").build());
        assertThat(teachers.findByVersionIdAndTeacherKey(v.getId(), "tamm_t")).isPresent();

        Lesson l = lessons.save(Lesson.builder().teacherId(t.getId()).weekday((short) 2).period((short) 3)
                .classes("5a").subject("mat").room("101").note("").build());
        Lesson loadedLesson = lessons.findById(l.getId()).orElseThrow();
        assertThat(loadedLesson.getWeekday()).isEqualTo((short) 2);
        assertThat(lessons.findByTeacherIdInAndWeekday(List.of(t.getId()), (short) 2)).hasSize(1);

        LocalDate date = LocalDate.of(2026, 10, 15);
        Absence a = absences.save(Absence.builder().absenceDate(date).teacherKey("tamm_t")
                .teacherName("Tamm Testnimi").fromPeriod((short) 1).toPeriod((short) 4)
                .createdBy(user.getId()).build());
        Absence loadedAbsence = absences.findById(a.getId()).orElseThrow();
        assertThat(loadedAbsence.getAbsenceDate()).isEqualTo(date);
        assertThat(loadedAbsence.getCreatedAt()).isNotNull();
        assertThat(absences.findDistinctDates()).contains(date);

        Substitution s = substitutions.save(Substitution.builder().absenceId(a.getId())
                .period((short) 3).classes("5a").subject("mat")
                .substituteKeys(List.of("k1", "k2")).substituteNames("A, B").build());
        Substitution loaded = substitutions.findById(s.getId()).orElseThrow();
        assertThat(loaded.getSubstituteKeys()).containsExactly("k1", "k2");
        assertThat(loaded.getFreeText()).isEmpty();
        assertThat(substitutions.findByAbsenceIdInOrderByPeriod(List.of(a.getId()))).hasSize(1);
    }

    @Test
    void dutiesRoundTrip() {
        AppUser user = users.save(AppUser.builder()
                .googleSub("schema-test-duty").email("schema-duty@example.com").name("Schema Duty").build());

        DutyVersion v = dutyVersions.save(DutyVersion.builder()
                .uploadedBy(user.getId()).fileName("duties.xlsx").warnings(List.of("w")).build());
        assertThat(dutyVersions.findFirstByOrderByIdDesc().orElseThrow().getId()).isEqualTo(v.getId());

        Duty d = duties.save(Duty.builder().versionId(v.getId()).weekday((short) 1).period((short) 2)
                .kind("OVT").starts(LocalTime.of(10, 45)).ends(LocalTime.of(11, 15))
                .classGroup("1.1 ja 1.2").rawName("Kask")
                .candidateKeys(List.of("a", "b")).build());
        Duty loadedDuty = duties.findById(d.getId()).orElseThrow();
        assertThat(loadedDuty.getCandidateKeys()).containsExactly("a", "b");
        assertThat(loadedDuty.getStarts()).isEqualTo(LocalTime.of(10, 45));
        assertThat(loadedDuty.getTeacherKey()).isNull();
        assertThat(loadedDuty.getPosition()).isEmpty();
        assertThat(duties.findByVersionIdOrderByWeekdayAscPeriodAscIdAsc(v.getId())).hasSize(1);
        assertThat(duties.findByVersionIdAndWeekdayAndTeacherKeyNotNull(v.getId(), (short) 1)).isEmpty();

        Absence a = absences.save(Absence.builder().absenceDate(LocalDate.of(2026, 10, 16))
                .teacherKey("tamm_t").teacherName("Tamm Testnimi").fromPeriod((short) 1).toPeriod((short) 4)
                .createdBy(user.getId()).build());
        Substitution s = substitutions.save(Substitution.builder().absenceId(a.getId())
                .period((short) 2).classes("").subject("").dutyLabel("ÕVT 10:45–11:15").build());
        assertThat(substitutions.findById(s.getId()).orElseThrow().getDutyLabel()).isEqualTo("ÕVT 10:45–11:15");
    }
}
