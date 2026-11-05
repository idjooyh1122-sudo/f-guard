package com.fguard.entity;

import com.fguard.domain.AssignmentSource;
import com.fguard.domain.AssignmentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "assignment")
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long courseId;
    private String title;
    private OffsetDateTime dueAt;
    private BigDecimal maxScore = BigDecimal.valueOf(100);
    private BigDecimal score;
    @Enumerated(EnumType.STRING)
    private AssignmentStatus status = AssignmentStatus.PENDING;
    @Enumerated(EnumType.STRING)
    private AssignmentSource source = AssignmentSource.MANUAL;
    private boolean confirmed = true;

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long v) { courseId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime v) { dueAt = v; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal v) { maxScore = v; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { score = v; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus v) { status = v; }
    public AssignmentSource getSource() { return source; }
    public void setSource(AssignmentSource v) { source = v; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean v) { confirmed = v; }
}
