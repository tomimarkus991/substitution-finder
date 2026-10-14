package com.asendus.api.timetable;

import java.time.Instant;
import java.util.List;

public final class TimetableDtos {
    private TimetableDtos() {}

    /** An uploaded version. added/removed are display names compared by key with the active version. */
    public record TimetablePreviewDto(Long id, String fileName, boolean active, int teacherCount, int lessonCount,
                                      List<String> addedTeachers, List<String> removedTeachers, List<String> warnings) {}

    public record TeacherSummaryDto(String key, String name, String displayName, boolean placeholder, int lessonCount) {}

    public record TimetableSummaryDto(Long id, String fileName, Instant uploadedAt, List<TeacherSummaryDto> teachers) {}

    public record LessonDto(int weekday, int period, String classes, String subject, String room, String note) {}

    public record TeacherGridDto(String key, String name, String displayName, boolean placeholder, List<LessonDto> lessons) {}

    public record PlaceholderRequest(boolean placeholder) {}
}
