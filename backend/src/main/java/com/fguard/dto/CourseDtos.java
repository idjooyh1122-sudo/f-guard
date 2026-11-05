package com.fguard.dto;

import com.fguard.domain.ComponentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public final class CourseDtos {
    private CourseDtos() {}

    public record ScheduleDto(
            Long id,
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 50) String room) {}

    public record ComponentDto(
            Long id,
            @NotNull ComponentType type,
            @NotBlank @Size(max = 50) String name,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal weight,
            @DecimalMin("0") @DecimalMax("100") BigDecimal score) {}

    public record CourseRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 50) String professor,
            @Min(0) @Max(10) Integer credits,
            @Min(1) @Max(200) Integer totalSessions,
            @Min(1) Integer absenceFailThreshold,
            @Min(1) Integer latesPerAbsence,
            @DecimalMin("0") @DecimalMax("100") BigDecimal absencePenalty,
            @DecimalMin("0") @DecimalMax("100") BigDecimal latePenalty,
            @DecimalMin("0") @DecimalMax("100") BigDecimal failCutoff,
            @Min(1) Integer missingAssignmentFailCount,
            @Size(max = 2000) String rulesNote,
            boolean rulesConfirmed,
            @Valid List<ScheduleDto> schedules,
            @Valid List<ComponentDto> components) {}

    public record CourseResponse(
            Long id,
            String name,
            String professor,
            Integer credits,
            Integer totalSessions,
            Integer absenceFailThreshold,
            Integer latesPerAbsence,
            BigDecimal absencePenalty,
            BigDecimal latePenalty,
            BigDecimal failCutoff,
            Integer missingAssignmentFailCount,
            String rulesNote,
            boolean rulesConfirmed,
            List<ScheduleDto> schedules,
            List<ComponentDto> components,
            BigDecimal weightSum) {}
}
