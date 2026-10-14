package com.asendus.api.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface TimetableVersionRepository extends JpaRepository<TimetableVersion, Long> {
    Optional<TimetableVersion> findByActiveTrue();

    List<TimetableVersion> findByActiveFalse();

    /** Bulk delete; the database cascades to teachers and lessons. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TimetableVersion v where v.active = false")
    int deleteInactive();
}
