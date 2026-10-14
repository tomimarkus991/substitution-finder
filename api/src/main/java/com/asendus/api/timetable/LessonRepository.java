package com.asendus.api.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByTeacherIdIn(Collection<Long> ids);

    List<Lesson> findByTeacherIdOrderByWeekdayAscPeriodAsc(Long teacherId);

    List<Lesson> findByTeacherIdInAndWeekday(Collection<Long> ids, short weekday);

    long countByTeacherIdIn(Collection<Long> ids);
}
