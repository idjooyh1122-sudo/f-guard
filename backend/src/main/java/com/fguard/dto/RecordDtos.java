package com.fguard.dto;

import com.fguard.domain.AssignmentSource;
import com.fguard.domain.AssignmentStatus;
import com.fguard.domain.AttendanceStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class RecordDtos {
    private RecordDtos() {}

    public record AttendanceRequest(
            @NotNull LocalDate classDate,
            @NotNull AttendanceStatus status,
            @Size(max = 200) String note) {}

    public record AttendanceResponse(Long id, Long courseId, LocalDate classDate, AttendanceStatus status, String note) {}

    public record AttendanceSummary(Long courseId, int attended, int late, int absent, int excused) {}

    public record AssignmentRequest(
            @NotBlank @Size(max = 150) String title,
            OffsetDateTime dueAt,
            @DecimalMin(value = "0", inclusive = false) BigDecimal maxScore,
            @DecimalMin("0") BigDecimal score,
            @NotNull AssignmentStatus status,
            Boolean confirmed) {}

    public record AssignmentResponse(
            Long id, Long courseId, String courseName, String title, OffsetDateTime dueAt,
            BigDecimal maxScore, BigDecimal score, AssignmentStatus status, AssignmentSource source,
            boolean confirmed, CalendarState calendar) {}

    public record CalendarState(boolean synced, String eventId, String htmlLink, boolean outdated) {}
}
