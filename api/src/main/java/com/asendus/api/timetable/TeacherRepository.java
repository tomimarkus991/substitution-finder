package com.asendus.api.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    List<Teacher> findByVersionIdOrderByName(Long versionId);

    Optional<Teacher> findByVersionIdAndTeacherKey(Long versionId, String key);

    long countByVersionId(Long versionId);
}
