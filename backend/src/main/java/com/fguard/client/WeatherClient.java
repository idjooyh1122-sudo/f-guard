package com.fguard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fguard.config.FGuardProperties;
import com.fguard.domain.ProviderMode;
import com.fguard.domain.WeatherSnapshot;
import com.fguard.domain.WeatherSnapshot.PrecipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기상청 단기예보 조회서비스 (공공데이터포털) — getVilageFcst.
 * 키가 없거나 호출에 실패하면 MOCK 스냅샷을 반환하고 mode=MOCK 으로 명시한다.
 */
@Component
public class WeatherClient {
    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HHmm");
    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};

    private final FGuardProperties.Weather props;
    private final Clock clock;
    private final RestClient restClient;
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    private record CachedResponse(JsonNode body, Instant fetchedAt) {}

    public WeatherClient(FGuardProperties properties, Clock clock, RestClient.Builder builder) {
        this.props = properties.weather();
        this.clock = clock;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(10));
        // 서비스키는 Decoding 키를 받아 한 번만 인코딩한다 (이중 인코딩 방지)
        DefaultUriBuilderFactory uf = new DefaultUriBuilderFactory(props.baseUrl());
        uf.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        this.restClient = builder.uriBuilderFactory(uf).requestFactory(rf).build();
    }

    public boolean isConfigured() {
        return props.configured();
    }

    public WeatherSnapshot forecast(double lat, double lon, LocalDateTime target) {
        if (!isConfigured()) {
            return mock(target, "기상청 API 키 미설정");
        }
        KmaGridConverter.Grid grid = KmaGridConverter.toGrid(lat, lon);
        LocalDateTime base = latestBaseTime(LocalDateTime.now(clock));
        try {
            JsonNode body = fetch(grid, base);
            return parse(body, target);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("기상청 API 호출 실패 → MOCK 사용: {}", e.getMessage());
            return mock(target, "기상청 API 호출 실패");
        }
    }

    private JsonNode fetch(KmaGridConverter.Grid grid, LocalDateTime base) {
        String key = grid.nx() + "," + grid.ny() + "@" + base;
        CachedResponse cached = cache.get(key);
        if (cached != null && cached.fetchedAt().isAfter(Instant.now(clock).minus(Duration.ofMinutes(30)))) {
            return cached.body();
        }
        JsonNode body = restClient.get()
                .uri(b -> b.path("/getVilageFcst")
                        .queryParam("serviceKey", props.serviceKey())
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 1000)
                        .queryParam("dataType", "JSON")
                        .queryParam("base_date", base.format(D))
                        .queryParam("base_time", base.format(T))
                        .queryParam("nx", grid.nx())
                        .queryParam("ny", grid.ny())
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (body == null) throw new IllegalStateException("빈 응답");
        String code = body.path("response").path("header").path("resultCode").asText();
        if (!"00".equals(code)) {
            throw new IllegalStateException("resultCode=" + code + " " + body.path("response").path("header").path("resultMsg").asText());
        }
        cache.put(key, new CachedResponse(body, Instant.now(clock)));
        return body;
    }

    /** 단기예보 발표시각(02,05,...,23시) 중 API 제공(발표 후 약 10분)이 끝난 가장 최근 시각. */
    static LocalDateTime latestBaseTime(LocalDateTime now) {
        LocalDateTime ref = now.minusMinutes(15);
        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            if (ref.getHour() >= BASE_HOURS[i]) {
                return ref.toLocalDate().atTime(BASE_HOURS[i], 0);
            }
        }
        return ref.toLocalDate().minusDays(1).atTime(23, 0);
    }

    static WeatherSnapshot parse(JsonNode body, LocalDateTime target) {
        // 예보 시각별 카테고리 값 수집 → target 이상의 가장 가까운 예보 시각 선택
        Map<LocalDateTime, Map<String, String>> byTime = new HashMap<>();
        for (JsonNode item : body.path("response").path("body").path("items").path("item")) {
            LocalDateTime t = LocalDateTime.of(
                    LocalDate.parse(item.path("fcstDate").asText(), D),
                    LocalTime.parse(item.path("fcstTime").asText(), T));
            byTime.computeIfAbsent(t, k -> new HashMap<>()).put(item.path("category").asText(), item.path("fcstValue").asText());
        }
        if (byTime.isEmpty()) throw new IllegalStateException("예보 항목 없음");
        LocalDateTime targetHour = target.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime chosen = byTime.keySet().stream()
                .filter(t -> !t.isBefore(targetHour)).min(LocalDateTime::compareTo)
                .orElse(byTime.keySet().stream().max(LocalDateTime::compareTo).orElseThrow());
        Map<String, String> v = byTime.get(chosen);
        return new WeatherSnapshot(
                PrecipType.fromKmaCode(v.get("PTY")),
                parseInt(v.get("POP")),
                parseDouble(v.get("TMP")),
                parseDouble(v.get("WSD")),
                v.get("PCP"),
                chosen,
                ProviderMode.REAL,
                "기상청 단기예보");
    }

    private WeatherSnapshot mock(LocalDateTime target, String reason) {
        // 시연용 고정값: 비 예보. 반드시 MOCK 으로 표시된다.
        return new WeatherSnapshot(PrecipType.RAIN, 70, 12.0, 3.5, "1.0mm",
                target.withMinute(0).withSecond(0).withNano(0), ProviderMode.MOCK, "MOCK 데이터 (" + reason + ")");
    }

    private static Integer parseInt(String s) {
        try { return s == null ? null : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDouble(String s) {
        try { return s == null ? null : Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
