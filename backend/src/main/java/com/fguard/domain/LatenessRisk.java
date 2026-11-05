package com.fguard.domain;

import java.time.LocalDateTime;
import java.util.List;

public record LatenessRisk(
        int score,
        Level level,
        LocalDateTime classStart,
        int baseTravelMinutes,
        int adjustedTravelMinutes,
        int prepMinutes,
        int bufferMinutes,
        LocalDateTime recommendedDeparture,
        LocalDateTime startPreparingBy,
        Integer expectedLateMinutes,
        List<Factor> factors,
        WeatherSnapshot weather,
        String travelSource) {

    public enum Level { LOW, MEDIUM, HIGH, CRITICAL }

    /** 점수에 기여한 규칙. 모든 점수는 이 목록의 합으로 재현 가능하다. */
    public record Factor(String code, String description, int points) {}
}
