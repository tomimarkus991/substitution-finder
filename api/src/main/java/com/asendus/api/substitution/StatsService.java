package com.asendus.api.substitution;

import com.asendus.api.timetable.TeacherKey;
import com.asendus.api.timetable.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** How often each teacher has been a substitute, and how many lessons they missed: the Statistika page. */
@Service
@RequiredArgsConstructor
public class StatsService {
    private final SubstitutionRepository substitutionRepository;
    private final TimetableService timetableService;

    /** One row per active, non-placeholder teacher, zeros included. */
    public record TeacherStatsDto(String key, String name, int lessonCovers, int dutyCovers, int totalCovers,
                                  int missedLessons) {}

    private record Covers(int lessons, int duties) {}

    private static final Comparator<TeacherStatsDto> ORDER = Comparator
            .comparingInt(TeacherStatsDto::lessonCovers)
            .thenComparing(TeacherStatsDto::name, TeacherKey.DISPLAY_ORDER);

    /** Open ends of the range; Postgres dates cover both. */
    private static final LocalDate EARLIEST = LocalDate.of(1, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    /** Covers and missed lessons on absences from `from` to `to`, both inclusive; a null end is open. */
    @Transactional(readOnly = true)
    public List<TeacherStatsDto> stats(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Algus peab olema enne lõppu");
        }
        LocalDate start = from == null ? EARLIEST : from, end = to == null ? LATEST : to;
        Map<String, Covers> covers = new HashMap<>();
        for (Object[] r : substitutionRepository.countCoversByTeacherKey(start, end)) {
            covers.put((String) r[0], new Covers(((Number) r[1]).intValue(), ((Number) r[2]).intValue()));
        }
        Map<String, Integer> missed = new HashMap<>();
        for (Object[] r : substitutionRepository.countMissedLessonsByTeacherKey(start, end)) {
            missed.put((String) r[0], ((Number) r[1]).intValue());
        }
        Set<String> placeholders = timetableService.placeholderKeys();
        Map<String, String> overrides = timetableService.nameOverrides();
        return timetableService.activeTeachers().stream()
                .filter(t -> !placeholders.contains(t.getTeacherKey()))
                .map(t -> {
                    Covers c = covers.getOrDefault(t.getTeacherKey(), new Covers(0, 0));
                    return new TeacherStatsDto(t.getTeacherKey(), TimetableService.displayName(t, overrides),
                            c.lessons(), c.duties(), c.lessons() + c.duties(),
                            missed.getOrDefault(t.getTeacherKey(), 0));
                })
                .sorted(ORDER)
                .toList();
    }
}
