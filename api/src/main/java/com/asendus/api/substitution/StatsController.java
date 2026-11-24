package com.asendus.api.substitution;

import com.asendus.api.substitution.StatsService.TeacherStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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
     * Counts absences dated from {@code from} to {@code to}, both inclusive and optional.
     */
    @GetMapping("/stats")
    public List<TeacherStatsDto> stats(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return statsService.stats(from, to);
    }
}
