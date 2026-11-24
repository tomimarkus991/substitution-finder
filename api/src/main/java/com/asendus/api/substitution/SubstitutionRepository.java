package com.asendus.api.substitution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface SubstitutionRepository extends JpaRepository<Substitution, Long> {
    List<Substitution> findByAbsenceIdInOrderByPeriod(Collection<Long> absenceIds);

    /**
     * [teacher key, lesson covers, duty covers] for every key that is a substitute on at least one row.
     * A shared row counts once for each of its substitutes; a row with no substitutes counts for nobody.
     */
    @Query(value = """
            select k.key,
                   count(distinct s.id) filter (where s.duty_label is null),
                   count(distinct s.id) filter (where s.duty_label is not null)
            from substitution s
            cross join unnest(s.substitute_keys) as k(key)
            group by k.key
            """, nativeQuery = true)
    List<Object[]> countCoversByTeacherKey();

    /**
     * [teacher key, missed lessons] for every key with at least one: the rows without a duty label
     * that belong to that teacher's own absences. Missed duties are not counted.
     */
    @Query(value = """
            select a.teacher_key, count(*)
            from substitution s
            join absence a on a.id = s.absence_id
            where s.duty_label is null
            group by a.teacher_key
            """, nativeQuery = true)
    List<Object[]> countMissedLessonsByTeacherKey();
}
