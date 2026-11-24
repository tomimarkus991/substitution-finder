package com.asendus.api;

import com.asendus.api.duty.DutyService;
import com.asendus.api.model.AppUser;
import com.asendus.api.service.AuthService;
import com.asendus.api.substitution.Availability;
import com.asendus.api.substitution.DayDtos.AbsenceDto;
import com.asendus.api.substitution.DayDtos.CandidateDto;
import com.asendus.api.substitution.DayDtos.CreateAbsenceRequest;
import com.asendus.api.substitution.DayDtos.SubstitutionDto;
import com.asendus.api.substitution.DayDtos.UpdateSubstitutionRequest;
import com.asendus.api.substitution.SubstitutionService;
import com.asendus.api.timetable.Lesson;
import com.asendus.api.timetable.Teacher;
import com.asendus.api.timetable.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes sure the dev user exists, and that the school day the app opens on (today, or Monday on a weekend) and
 * the 10 school days before it have sample absences: one for periods 3–7 with its break duties and every other
 * lesson covered, one for the whole day with none. Teachers with a break duty are absent first, so the
 * Statistika page has duty covers too.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AuthService authService;
    private final SubstitutionService substitutionService;
    private final TimetableService timetableService;
    private final DutyService dutyService;

    /** School days seeded, the last one being the day the app opens on. */
    static final int SEEDED_DAYS = 11;

    @Override
    public void run(String... args) {
        AppUser devUser = authService.getOrCreateDevUser();
        log.info("[DataSeeder] Dev user ready: {} (id {}).", devUser.getEmail(), devUser.getId());
        seedRecent(schoolDay(LocalDate.now()), SEEDED_DAYS, devUser.getId());
    }

    /** The date itself on a weekday, the next Monday on a weekend. */
    static LocalDate schoolDay(LocalDate date) {
        return date.getDayOfWeek().getValue() > 5 ? date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) : date;
    }

    /** Seeds `days` school days (Monday to Friday) ending on `last`, oldest first. */
    void seedRecent(LocalDate last, int days, Long userId) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = last; dates.size() < days; d = d.minusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) dates.add(d);
        }
        dates.reversed().forEach(d -> seedDay(d, userId));
    }

    /** Adds the sample absences, unless there is no active timetable or the day already has absences. */
    void seedDay(LocalDate date, Long userId) {
        if (!timetableService.hasActiveVersion()) {
            log.info("[DataSeeder] No active timetable, so no sample absences. Upload one on the Tunniplaan page.");
            return;
        }
        if (!substitutionService.day(date).absences().isEmpty()) return;

        int weekday = date.getDayOfWeek().getValue();
        Set<String> placeholders = timetableService.placeholderKeys();
        // Teachers with a break duty in periods 3–7 first, so the partial absence gets duty rows
        Set<String> onDuty = dutyService.dutiesOn(weekday).entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(d -> d.getPeriod() >= 3))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<String> busiest = timetableService.lessonsOn(weekday).entrySet().stream()
                .filter(e -> !placeholders.contains(e.getKey().getTeacherKey()))
                .sorted(Comparator.comparing((Map.Entry<Teacher, List<Lesson>> e) -> !onDuty.contains(e.getKey().getTeacherKey()))
                        .thenComparing(Comparator.comparingLong((Map.Entry<Teacher, List<Lesson>> e) -> lessonsFrom3(e.getValue()))
                                .reversed())
                        .thenComparing(e -> e.getKey().getTeacherKey()))
                .map(e -> e.getKey().getTeacherKey())
                .limit(2)
                .toList();
        if (busiest.size() < 2) {
            log.info("[DataSeeder] The active timetable has too few lessons on {}, so no sample absences.", date);
            return;
        }

        AbsenceDto partial = substitutionService.createAbsence(date, new CreateAbsenceRequest(busiest.get(0), 3, 7), userId);
        substitutionService.createAbsence(date, new CreateAbsenceRequest(busiest.get(1), 1, 7), userId);
        // Cover every break duty and every other lesson, so the day shows both picked and open rows
        int lesson = 0;
        for (SubstitutionDto row : partial.rows()) {
            if (row.dutyLabel() == null && lesson++ % 2 == 1) continue;
            substitutionService.candidates(date, row.period(), row.id()).stream()
                    .filter(c -> c.status() == Availability.FREE)
                    .map(CandidateDto::key)
                    .findFirst()
                    .ifPresent(key -> substitutionService.updateSubstitution(row.id(),
                            new UpdateSubstitutionRequest(List.of(key), "")));
        }
        log.info("[DataSeeder] Added sample absences on {}.", date);
    }

    private static long lessonsFrom3(List<Lesson> lessons) {
        return lessons.stream().filter(l -> l.getPeriod() >= 3).count();
    }
}
