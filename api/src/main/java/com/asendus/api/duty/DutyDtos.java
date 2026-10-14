package com.asendus.api.duty;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class DutyDtos {
    private DutyDtos() {}

    /** dutyCount / teacherCount count matched duties only. */
    public record DutySummaryDto(String fileName, Instant uploadedAt, int dutyCount, int teacherCount,
                                 List<String> warnings, List<UnresolvedDutyDto> unresolved) {}

    /** A duty whose surname matched several teachers (candidates) or none (empty). label e.g. "ÕVT 10:45–11:15". */
    public record UnresolvedDutyDto(Long id, int weekday, String label, String classGroup, String rawName,
                                    List<TeacherOptionDto> candidates) {}

    public record TeacherOptionDto(String key, String name) {}

    public record ResolveDutyRequest(@NotBlank String teacherKey) {}
}
