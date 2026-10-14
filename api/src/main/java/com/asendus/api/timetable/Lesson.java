package com.asendus.api.timetable;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lesson")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false)
    private short weekday;

    @Column(nullable = false)
    private short period;

    @Column(nullable = false)
    private String classes;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String room;

    @Column(nullable = false)
    private String note;
}
