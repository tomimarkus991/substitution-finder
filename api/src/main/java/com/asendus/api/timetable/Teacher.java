package com.asendus.api.timetable;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teacher")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "key", nullable = false)
    private String teacherKey;

    @Column(nullable = false)
    private String name;
}
