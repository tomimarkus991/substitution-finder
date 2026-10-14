package com.asendus.api.timetable;

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
@Table(name = "placeholder_flag")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceholderFlag {

    @Id
    @Column(name = "teacher_key")
    private String teacherKey;
}
