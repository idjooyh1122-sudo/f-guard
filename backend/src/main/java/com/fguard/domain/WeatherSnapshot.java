package com.fguard.domain;

import java.time.LocalDateTime;

/**
 * 특정 시각의 날씨 예보 요약.
 * precipType 은 기상청 단기예보 PTY 코드 의미를 따른다.
 */
public record WeatherSnapshot(
        PrecipType precipType,
        Integer precipProbability,
        Double temperature,
        Double windSpeed,
        String precipitationText,
        LocalDateTime forecastTime,
        ProviderMode mode,
        String source) {

    public enum PrecipType {
        NONE("강수 없음"), RAIN("비"), RAIN_SNOW("비/눈"), SNOW("눈"), SHOWER("소나기");
        public final String label;
        PrecipType(String label) { this.label = label; }

        /** 기상청 단기예보 PTY 코드 → 강수형태 (0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기) */
        public static PrecipType fromKmaCode(String code) {
            return switch (code == null ? "0" : code.trim()) {
                case "1" -> RAIN;
                case "2" -> RAIN_SNOW;
                case "3" -> SNOW;
                case "4" -> SHOWER;
                default -> NONE;
            };
        }
    }
}
