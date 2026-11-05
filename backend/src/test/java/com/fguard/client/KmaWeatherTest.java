package com.fguard.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fguard.domain.ProviderMode;
import com.fguard.domain.WeatherSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaWeatherTest {

    @Test
    void 격자변환_서울시청은_60_127() {
        KmaGridConverter.Grid g = KmaGridConverter.toGrid(37.5665, 126.9780);
        assertThat(g.nx()).isEqualTo(60);
        assertThat(g.ny()).isEqualTo(127);
    }

    @Test
    void 발표시각은_가장_최근_제공시각() {
        assertThat(WeatherClient.latestBaseTime(LocalDateTime.of(2026, 11, 5, 8, 20))).isEqualTo(LocalDateTime.of(2026, 11, 5, 8, 0));
        assertThat(WeatherClient.latestBaseTime(LocalDateTime.of(2026, 11, 5, 8, 10))).isEqualTo(LocalDateTime.of(2026, 11, 5, 5, 0));
        assertThat(WeatherClient.latestBaseTime(LocalDateTime.of(2026, 11, 5, 1, 0))).isEqualTo(LocalDateTime.of(2026, 11, 4, 23, 0));
    }

    @Test
    void 단기예보_응답_파싱() throws Exception {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                 "body":{"items":{"item":[
                  {"category":"PTY","fcstDate":"20261105","fcstTime":"0800","fcstValue":"0"},
                  {"category":"PTY","fcstDate":"20261105","fcstTime":"0900","fcstValue":"1"},
                  {"category":"POP","fcstDate":"20261105","fcstTime":"0900","fcstValue":"80"},
                  {"category":"TMP","fcstDate":"20261105","fcstTime":"0900","fcstValue":"7"},
                  {"category":"WSD","fcstDate":"20261105","fcstTime":"0900","fcstValue":"4.2"},
                  {"category":"PCP","fcstDate":"20261105","fcstTime":"0900","fcstValue":"1.0mm"}
                 ]}}}}""";
        WeatherSnapshot w = WeatherClient.parse(new ObjectMapper().readTree(json), LocalDateTime.of(2026, 11, 5, 8, 50));
        assertThat(w.forecastTime()).isEqualTo(LocalDateTime.of(2026, 11, 5, 8, 0));
        WeatherSnapshot w9 = WeatherClient.parse(new ObjectMapper().readTree(json), LocalDateTime.of(2026, 11, 5, 9, 0));
        assertThat(w9.precipType()).isEqualTo(WeatherSnapshot.PrecipType.RAIN);
        assertThat(w9.precipProbability()).isEqualTo(80);
        assertThat(w9.windSpeed()).isEqualTo(4.2);
        assertThat(w9.mode()).isEqualTo(ProviderMode.REAL);
    }
}
