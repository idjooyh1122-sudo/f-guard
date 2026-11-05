package com.fguard.service;

import com.fguard.domain.LatenessRisk;
import com.fguard.domain.LatenessRisk.Factor;
import com.fguard.domain.LatenessRisk.Level;
import com.fguard.domain.WeatherSnapshot;
import com.fguard.domain.WeatherSnapshot.PrecipType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 규칙 기반 지각 위험 점수(0~100). 학습 데이터가 없으므로 확률(%)이 아니라
 * 검증 가능한 규칙 점수의 합으로 계산한다. GPT 는 이 결과를 "설명"만 한다.
 */
@Component
public class LatenessRiskCalculator {

    public record Input(
            LocalDateTime classStart,
            LocalDateTime now,
            int prepMinutes,
            int baseTravelMinutes,
            int bufferMinutes,
            WeatherSnapshot weather,
            int recentLateCount,
            int recentSessionCount,
            String travelSource) {}

    public LatenessRisk evaluate(Input in) {
        List<Factor> factors = new ArrayList<>();

        // 1) 날씨에 따른 이동시간 보정
        double travelFactor = 0.0;
        WeatherSnapshot w = in.weather();
        if (w != null) {
            PrecipType p = w.precipType() == null ? PrecipType.NONE : w.precipType();
            switch (p) {
                case SNOW, RAIN_SNOW -> {
                    travelFactor += 0.4;
                    factors.add(new Factor("WEATHER_SNOW", "예보: %s (이동시간 +40%%)".formatted(p.label), 30));
                }
                case RAIN -> {
                    travelFactor += 0.2;
                    factors.add(new Factor("WEATHER_RAIN", "예보: 비 (이동시간 +20%)", 20));
                }
                case SHOWER -> {
                    travelFactor += 0.15;
                    factors.add(new Factor("WEATHER_SHOWER", "예보: 소나기 (이동시간 +15%)", 15));
                }
                case NONE -> {
                    if (w.precipProbability() != null && w.precipProbability() >= 60) {
                        travelFactor += 0.1;
                        factors.add(new Factor("WEATHER_POP", "강수확률 %d%% (이동시간 +10%%)".formatted(w.precipProbability()), 10));
                    }
                }
            }
            if (w.windSpeed() != null && w.windSpeed() >= 9.0) {
                travelFactor += 0.1;
                factors.add(new Factor("WEATHER_WIND", "강풍 %.1fm/s (이동시간 +10%%)".formatted(w.windSpeed()), 10));
            }
        }
        int adjustedTravel = (int) Math.ceil(in.baseTravelMinutes() * (1.0 + travelFactor));

        LocalDateTime departure = in.classStart().minusMinutes(adjustedTravel + in.bufferMinutes());
        LocalDateTime prepStart = departure.minusMinutes(in.prepMinutes());

        // 2) 과거 지각 이력
        if (in.recentSessionCount() > 0 && in.recentLateCount() > 0) {
            double rate = (double) in.recentLateCount() / in.recentSessionCount();
            int pts = rate >= 0.3 ? 20 : rate >= 0.15 ? 10 : 5;
            factors.add(new Factor("HISTORY", "최근 30일 지각 %d회 / 수업 %d회".formatted(in.recentLateCount(), in.recentSessionCount()), pts));
        }

        // 3) 이른 수업 (1교시)
        if (in.classStart().toLocalTime().isBefore(LocalTime.of(9, 30))) {
            factors.add(new Factor("EARLY_CLASS", "오전 9시 30분 이전 수업", 5));
        }

        // 4) 현재 시각 기준 남은 여유
        Integer expectedLate = null;
        if (in.now() != null && in.now().isBefore(in.classStart())) {
            LocalDateTime arrival = in.now().plusMinutes(in.prepMinutes() + adjustedTravel);
            long lateMin = Duration.between(in.classStart(), arrival).toMinutes();
            if (lateMin > 0) {
                expectedLate = (int) lateMin;
                factors.add(new Factor("TIME_LATE", "지금 준비를 시작해도 약 %d분 지각 예상".formatted(lateMin), 50));
            } else if (-lateMin < in.bufferMinutes()) {
                factors.add(new Factor("TIME_TIGHT", "도착 여유가 %d분뿐입니다".formatted(-lateMin), 25));
            } else if (Duration.between(in.now(), prepStart).toMinutes() < 15) {
                factors.add(new Factor("TIME_SOON", "15분 안에 준비를 시작해야 합니다", 10));
            }
        }

        int score = Math.min(100, factors.stream().mapToInt(Factor::points).sum());
        Level level = score >= 75 ? Level.CRITICAL : score >= 50 ? Level.HIGH : score >= 25 ? Level.MEDIUM : Level.LOW;

        return new LatenessRisk(score, level, in.classStart(), in.baseTravelMinutes(), adjustedTravel,
                in.prepMinutes(), in.bufferMinutes(), departure, prepStart, expectedLate,
                List.copyOf(factors), w, in.travelSource());
    }
}
