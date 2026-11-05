package com.fguard.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fguard.domain.AnalysisStatus;
import com.fguard.domain.ProviderMode;
import com.fguard.domain.WeatherSnapshot;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    /** GPT 또는 MOCK 이 생성한 설명 */
    public record Explanation(String summary, List<String> actions, ProviderMode mode, String model, String error) {}

    public record SyllabusAnalysisResponse(
            Long id, Long courseId, String fileName, long fileSize, AnalysisStatus status,
            ProviderMode mode, String model, JsonNode result, List<String> validationIssues,
            String errorMessage, OffsetDateTime createdAt, OffsetDateTime confirmedAt) {}

    /** 사용자가 검토·수정한 분석 결과를 확정 저장 */
    public record SyllabusConfirmRequest(
            Long courseId, // null 이면 새 과목 생성
            @NotNull JsonNode result) {}

    public record ExcuseRequest(
            Long courseId,
            @NotNull LocalDate classDate,
            @NotBlank @Size(max = 1000) String reason,
            @Min(0) @Max(300) Integer minutesLate,
            @Size(max = 30) String studentName,
            @Size(max = 20) String studentId,
            boolean includeWeather) {}

    public record ExcuseResponse(
            String letter, String message, List<String> citedFacts, List<String> cautions,
            WeatherSnapshot weather, boolean weatherCited, ProviderMode mode, String model, String error) {}
}
