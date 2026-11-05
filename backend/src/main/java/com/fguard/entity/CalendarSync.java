package com.fguard.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "calendar_sync")
public class CalendarSync {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long assignmentId;
    private String googleEventId;
    private String htmlLink;
    private OffsetDateTime syncedDueAt;
    private OffsetDateTime syncedAt;

    public Long getId() { return id; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long v) { assignmentId = v; }
    public String getGoogleEventId() { return googleEventId; }
    public void setGoogleEventId(String v) { googleEventId = v; }
    public String getHtmlLink() { return htmlLink; }
    public void setHtmlLink(String v) { htmlLink = v; }
    public OffsetDateTime getSyncedDueAt() { return syncedDueAt; }
    public void setSyncedDueAt(OffsetDateTime v) { syncedDueAt = v; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(OffsetDateTime v) { syncedAt = v; }
}
