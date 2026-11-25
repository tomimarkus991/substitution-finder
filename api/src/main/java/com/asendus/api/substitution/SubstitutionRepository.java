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
     * Only rows whose absence date lies in [from, to], both inclusive.
     */
    @Query(value = """
            select k.key,
                   count(distinct s.id) filter (where s.duty_label is null),
                   count(distinct s.id) filter (where s.duty_label is not null)
            from substitution s
            join absence a on a.id = s.absence_id
            cross join unnest(s.substitute_keys) as k(key)
            where a.date between :from and :to
            group by k.key
            """, nativeQuery = true)
    List<Object[]> countCoversByTeacherKey(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * [teacher key, missed lessons] for every teacher with an absence dated in [from, to]: the lesson rows of
     * their absences. Periods without a lesson have no row; break duty rows don't count.
     */
    @Query(value = """
            select a.teacher_key, count(s.id)
            from absence a
            join substitution s on s.absence_id = a.id and s.duty_label is null
            where a.date between :from and :to
            group by a.teacher_key
            """, nativeQuery = true)
    List<Object[]> countMissedLessonsByTeacherKey(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
