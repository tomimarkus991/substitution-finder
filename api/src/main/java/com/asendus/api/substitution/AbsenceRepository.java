package com.asendus.api.substitution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.*;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {
    List<Absence> findByAbsenceDateOrderByTeacherName(LocalDate date);

    boolean existsByAbsenceDateAndTeacherKey(LocalDate d, String key);

    @Query("select distinct a.absenceDate from Absence a order by a.absenceDate desc")
    List<LocalDate> findDistinctDates();
}
