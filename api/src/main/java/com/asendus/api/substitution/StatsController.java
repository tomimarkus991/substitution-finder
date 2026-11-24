package com.asendus.api.substitution;

import com.asendus.api.substitution.StatsService.TeacherStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    /** Covers per active teacher, fewest lesson covers first; empty if there is no active timetable. */
    @GetMapping("/stats")
    public List<TeacherStatsDto> stats() {
        return statsService.stats();
    }
}
