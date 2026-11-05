package com.fguard.entity;

import com.fguard.domain.AnalysisStatus;
import com.fguard.domain.ProviderMode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "syllabus_analysis")
public class SyllabusAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long courseId;
    private String fileName;
    private String contentType;
    private long fileSize;
    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;
    @Enumerated(EnumType.STRING)
    private ProviderMode providerMode;
    private String model;
    @JdbcTypeCode(SqlTypes.JSON)
    private String resultJson;
    private String errorMessage;
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    private OffsetDateTime confirmedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long v) { courseId = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { fileName = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { contentType = v; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long v) { fileSize = v; }
    public AnalysisStatus getStatus() { return status; }
    public void setStatus(AnalysisStatus v) { status = v; }
    public ProviderMode getProviderMode() { return providerMode; }
    public void setProviderMode(ProviderMode v) { providerMode = v; }
    public String getModel() { return model; }
    public void setModel(String v) { model = v; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String v) { resultJson = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime v) { confirmedAt = v; }
}
