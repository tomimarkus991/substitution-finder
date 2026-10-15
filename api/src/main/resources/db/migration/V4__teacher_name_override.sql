-- A display name set by hand, by teacher key, so it carries over to later timetable versions.
CREATE TABLE teacher_name_override (
    teacher_key  TEXT PRIMARY KEY,
    display_name TEXT NOT NULL CHECK (length(trim(display_name)) > 0)
);
