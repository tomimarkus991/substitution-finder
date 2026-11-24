package com.asendus.api.substitution;

import com.asendus.api.substitution.StatsService.TeacherStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    /**
     * Covers per active teacher, fewest lesson covers first; empty if there is no active timetable.
     * Optional `from` and `to` (ISO dates, inclusive) count only covers on absences in that range.
     */
    @GetMapping("/stats")
    public List<TeacherStatsDto> stats(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to) {
        return statsService.stats(from, to);
    }
}
