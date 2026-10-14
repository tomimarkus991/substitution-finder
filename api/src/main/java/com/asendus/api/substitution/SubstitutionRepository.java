package com.asendus.api.substitution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface SubstitutionRepository extends JpaRepository<Substitution, Long> {
    List<Substitution> findByAbsenceIdInOrderByPeriod(Collection<Long> absenceIds);
}
