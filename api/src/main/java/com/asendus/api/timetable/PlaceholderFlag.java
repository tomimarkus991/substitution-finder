package com.asendus.api.timetable;

import jakarta.persistence.*;
import lombok.*;

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
