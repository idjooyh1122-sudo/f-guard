package com.fguard.service;

import com.fguard.domain.LatenessRisk;
import com.fguard.domain.ProviderMode;
import com.fguard.domain.WeatherSnapshot;
import com.fguard.domain.WeatherSnapshot.PrecipType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LatenessRiskCalculatorTest {
    final LatenessRiskCalculator calc = new LatenessRiskCalculator();
    static final LocalDateTime CLASS = LocalDateTime.of(2026, 11, 5, 9, 0);

    static WeatherSnapshot weather(PrecipType p, int pop, double wind) {
        return new WeatherSnapshot(p, pop, 10.0, wind, null, CLASS, ProviderMode.REAL, "test");
    }

    @Test
    void 맑은날_여유있으면_낮음_그리고_출발시각_계산() {
        LatenessRisk r = calc.evaluate(new LatenessRiskCalculator.Input(LocalDateTime.of(2026, 11, 5, 10, 30),
                LocalDateTime.of(2026, 11, 5, 7, 0), 40, 30, 10, weather(PrecipType.NONE, 10, 2), 0, 10, "manual"));
        assertThat(r.level()).isEqualTo(LatenessRisk.Level.LOW);
        assertThat(r.score()).isZero();
        assertThat(r.adjustedTravelMinutes()).isEqualTo(30);
        assertThat(r.recommendedDeparture()).isEqualTo(LocalDateTime.of(2026, 11, 5, 9, 50));
        assertThat(r.startPreparingBy()).isEqualTo(LocalDateTime.of(2026, 11, 5, 9, 10));
    }

    @Test
    void 눈_예보는_이동시간_40퍼센트_증가() {
        LatenessRisk r = calc.evaluate(new LatenessRiskCalculator.Input(CLASS, null, 40, 30, 10,
                weather(PrecipType.SNOW, 80, 2), 0, 0, "manual"));
        assertThat(r.adjustedTravelMinutes()).isEqualTo(42);
        assertThat(r.recommendedDeparture()).isEqualTo(CLASS.minusMinutes(52));
        assertThat(r.factors()).extracting(LatenessRisk.Factor::code).contains("WEATHER_SNOW", "EARLY_CLASS");
        assertThat(r.score()).isEqualTo(35);
        assertThat(r.level()).isEqualTo(LatenessRisk.Level.MEDIUM);
    }

    @Test
    void 지금_출발해도_늦으면_CRITICAL에_가까운_점수() {
        // 08:30 현재, 준비 40 + 이동 30×1.3(비 +20%, 강풍 +10%)=39 → 09:49 도착 → 49분 지각
        LatenessRisk r = calc.evaluate(new LatenessRiskCalculator.Input(CLASS, LocalDateTime.of(2026, 11, 5, 8, 30),
                40, 30, 10, weather(PrecipType.RAIN, 90, 10), 4, 10, "manual"));
        assertThat(r.expectedLateMinutes()).isEqualTo(49);
        // 비20 + 강풍10 + 이력(40%)20 + 1교시5 + 지각예상50 = 105 → 100 캡
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.level()).isEqualTo(LatenessRisk.Level.CRITICAL);
    }

    @Test
    void 점수는_요인_점수의_합과_같다() {
        LatenessRisk r = calc.evaluate(new LatenessRiskCalculator.Input(CLASS, null, 30, 20, 5,
                weather(PrecipType.NONE, 70, 1), 1, 10, "manual"));
        int sum = r.factors().stream().mapToInt(LatenessRisk.Factor::points).sum();
        assertThat(r.score()).isEqualTo(Math.min(100, sum)).isEqualTo(10 + 5 + 5);
    }
}
