package com.asendus.api.substitution;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class DayDtos {
    private DayDtos() {}

    /** One lesson (or break duty, when dutyLabel is set) of an absent teacher. substituteNames are display names joined with " / ". */
    public record SubstitutionDto(Long id, int period, String classes, String subject,
                                  List<String> substituteKeys, String substituteNames, String freeText,
                                  String dutyLabel) {}

    public record AbsenceDto(Long id, String teacherKey, String teacherName, int fromPeriod, int toPeriod,
                             List<SubstitutionDto> rows) {}

    public record DayDto(LocalDate date, int weekday, List<AbsenceDto> absences) {}

    /** fromPeriod defaults to 1 and toPeriod to 7. */
    public record CreateAbsenceRequest(@NotBlank String teacherKey,
                                       @Min(1) @Max(7) Integer fromPeriod,
                                       @Min(1) @Max(7) Integer toPeriod) {}

    /**
     * duties: labels of the candidate's break duties in the requested period. day: their 7 periods.
     * streak: periods in a row they'd work if they take this one.
     */
    public record CandidateDto(String key, String name, Availability status, List<String> subjects, int todayCount,
                               List<String> duties, List<DaySlot> day, int streak) {}

    /** One period of a candidate's day. label: "4R · Ek" (several joined with "; "), a duty label, or "". */
    public record DaySlot(int period, SlotKind kind, String label) {}

    public record UpdateSubstitutionRequest(@NotNull @Size(max = 10) List<String> substituteKeys,
                                            @NotNull @Size(max = 200) String freeText) {}
}
