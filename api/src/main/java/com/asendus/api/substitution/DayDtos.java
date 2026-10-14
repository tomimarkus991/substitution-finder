package com.asendus.api.substitution;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public final class DayDtos {
    private DayDtos() {}

    /** One lesson of an absent teacher. substituteNames are display names joined with " / ". */
    public record SubstitutionDto(Long id, int period, String classes, String subject,
                                  List<String> substituteKeys, String substituteNames, String freeText) {}

    public record AbsenceDto(Long id, String teacherKey, String teacherName, int fromPeriod, int toPeriod,
                             List<SubstitutionDto> rows) {}

    public record DayDto(LocalDate date, int weekday, List<AbsenceDto> absences) {}

    /** fromPeriod defaults to 1 and toPeriod to 7. */
    public record CreateAbsenceRequest(@NotBlank String teacherKey,
                                       @Min(1) @Max(7) Integer fromPeriod,
                                       @Min(1) @Max(7) Integer toPeriod) {}

    public record CandidateDto(String key, String name, Availability status, List<String> subjects, int todayCount) {}

    public record UpdateSubstitutionRequest(@NotNull List<String> substituteKeys, @NotNull String freeText) {}
}
