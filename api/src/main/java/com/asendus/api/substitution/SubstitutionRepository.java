package com.asendus.api.substitution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.*;

public interface SubstitutionRepository extends JpaRepository<Substitution, Long> {
    List<Substitution> findByAbsenceIdInOrderByPeriod(Collection<Long> absenceIds);

    /**
     * [teacher key, lesson covers, duty covers] for every key that is a substitute on at least one row.
     * A shared row counts once for each of its substitutes; a row with no substitutes counts for nobody.
     * Only absences dated from {@code from} to {@code to}, both inclusive; a null bound is open.
     */
    @Query(value = """
            select k.key,
                   count(distinct s.id) filter (where s.duty_label is null),
                   count(distinct s.id) filter (where s.duty_label is not null)
            from substitution s
            join absence a on a.id = s.absence_id
            cross join unnest(s.substitute_keys) as k(key)
            where (cast(:from as date) is null or a.date >= cast(:from as date))
              and (cast(:to as date) is null or a.date <= cast(:to as date))
            group by k.key
            """, nativeQuery = true)
    List<Object[]> countCoversByTeacherKey(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * [teacher key, missed lessons] for every key with at least one: the rows without a duty label
     * that belong to that teacher's own absences. Missed duties are not counted.
     * Only absences dated from {@code from} to {@code to}, both inclusive; a null bound is open.
     */
    @Query(value = """
            select a.teacher_key, count(*)
            from substitution s
            join absence a on a.id = s.absence_id
            where s.duty_label is null
              and (cast(:from as date) is null or a.date >= cast(:from as date))
              and (cast(:to as date) is null or a.date <= cast(:to as date))
            group by a.teacher_key
            """, nativeQuery = true)
    List<Object[]> countMissedLessonsByTeacherKey(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
