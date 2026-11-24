package com.asendus.api.substitution;

import com.asendus.api.timetable.TeacherKey;
import com.asendus.api.timetable.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<TeacherStatsDto> stats() {
        Map<String, Covers> covers = new HashMap<>();
        for (Object[] r : substitutionRepository.countCoversByTeacherKey()) {
            covers.put((String) r[0], new Covers(((Number) r[1]).intValue(), ((Number) r[2]).intValue()));
        }
        Map<String, Integer> missed = new HashMap<>();
        for (Object[] r : substitutionRepository.countMissedLessonsByTeacherKey()) {
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
