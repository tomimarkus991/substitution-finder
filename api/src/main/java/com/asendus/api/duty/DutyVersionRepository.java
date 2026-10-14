package com.asendus.api.duty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DutyVersionRepository extends JpaRepository<DutyVersion, Long> {
    Optional<DutyVersion> findFirstByOrderByIdDesc();
}
