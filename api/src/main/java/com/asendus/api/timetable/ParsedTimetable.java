package com.asendus.api.timetable;

import java.util.List;

public record ParsedTimetable(List<ParsedTeacher> teachers, List<String> warnings) {

    public record ParsedTeacher(String name, List<ParsedLesson> lessons) {}

    /** weekday 1 = Monday … 5 = Friday; period 1–7. classes/subject/room/note may be empty strings. */
    public record ParsedLesson(int weekday, int period, String classes, String subject, String room, String note) {}
}
