package com.asendus.api.substitution;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "substitution")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Substitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "absence_id", nullable = false)
    private Long absenceId;

    @Column(nullable = false)
    private short period;

    @Column(nullable = false)
    private String classes;

    @Column(nullable = false)
    private String subject;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "substitute_keys", nullable = false, columnDefinition = "text[]")
    private List<String> substituteKeys = new ArrayList<>();

    @Builder.Default
    @Column(name = "substitute_names", nullable = false)
    private String substituteNames = "";

    @Builder.Default
    @Column(name = "free_text", nullable = false)
    private String freeText = "";
}
