package com.asendus.api.substitution;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "absence")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate absenceDate;

    @Column(name = "teacher_key", nullable = false)
    private String teacherKey;

    @Column(name = "teacher_name", nullable = false)
    private String teacherName;

    @Column(name = "from_period", nullable = false)
    private short fromPeriod;

    @Column(name = "to_period", nullable = false)
    private short toPeriod;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
