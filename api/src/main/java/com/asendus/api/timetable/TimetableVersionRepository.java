package com.asendus.api.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TimetableVersionRepository extends JpaRepository<TimetableVersion, Long> {
    Optional<TimetableVersion> findByActiveTrue();

    List<TimetableVersion> findByActiveFalse();
}
