package com.fguard.entity;

import com.fguard.domain.AttendanceStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "attendance_record")
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long courseId;
    private LocalDate classDate;
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
    private String note;

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long v) { courseId = v; }
    public LocalDate getClassDate() { return classDate; }
    public void setClassDate(LocalDate v) { classDate = v; }
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus v) { status = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = v; }
}
