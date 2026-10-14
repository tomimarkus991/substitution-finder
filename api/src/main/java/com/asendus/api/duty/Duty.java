package com.asendus.api.duty;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "duty")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Duty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(nullable = false)
    private short weekday;

    @Column(nullable = false)
    private short period;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private LocalTime starts;

    @Column(nullable = false)
    private LocalTime ends;

    @Column(name = "class_group", nullable = false)
    private String classGroup;

    @Builder.Default
    @Column(nullable = false)
    private String position = "";

    @Column(name = "raw_name", nullable = false)
    private String rawName;

    @Column(name = "teacher_key")
    private String teacherKey;

    @Column(name = "teacher_name")
    private String teacherName;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "candidate_keys", nullable = false, columnDefinition = "text[]")
    private List<String> candidateKeys = new ArrayList<>();
}
