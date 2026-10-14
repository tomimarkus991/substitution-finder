package com.asendus.api.duty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DutyRepository extends JpaRepository<Duty, Long> {
    List<Duty> findByVersionIdOrderByWeekdayAscPeriodAscIdAsc(Long versionId);

    List<Duty> findByVersionIdAndWeekdayAndTeacherKeyNotNull(Long versionId, short weekday);
}
