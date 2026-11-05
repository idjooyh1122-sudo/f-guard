package com.fguard.entity;

import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "class_schedule")
public class ClassSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** ISO 요일: 1=월요일 ... 7=일요일 */
    private short dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;

    public ClassSchedule() {}

    public ClassSchedule(int dayOfWeek, LocalTime startTime, LocalTime endTime, String room) {
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public void setCourse(Course v) { course = v; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getRoom() { return room; }
}
