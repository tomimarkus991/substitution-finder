package com.asendus.api.timetable;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teacher_name_override")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherNameOverride {

    @Id
    @Column(name = "teacher_key")
    private String teacherKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;
}
