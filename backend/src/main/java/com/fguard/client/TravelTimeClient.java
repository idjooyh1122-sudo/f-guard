package com.fguard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fguard.config.FGuardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * 이동시간 조회.
 * <ul>
 *   <li>카카오모빌리티 길찾기 API(자동차 경로) 키가 있으면 GET /v1/directions 로 소요시간을 조회한다.</li>
 *   <li>키가 없거나 실패하면 사용자가 설정한 기본 이동시간(대중교통 평소 소요시간)을 사용한다.</li>
 * </ul>
 * 대중교통 경로 API 는 공식 공개 여부를 확인하지 못해 연동하지 않았다 (docs/IMPLEMENTATION.md 참고).
 */
@Component
public class TravelTimeClient {
    private static final Logger log = LoggerFactory.getLogger(TravelTimeClient.class);

    public record TravelTime(int minutes, String source, boolean live) {}

    private final FGuardProperties.Travel props;
    private final RestClient restClient;

    public TravelTimeClient(FGuardProperties properties, RestClient.Builder builder) {
        this.props = properties.travel();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = builder.baseUrl(props.kakaoBaseUrl()).requestFactory(rf).build();
    }

    public boolean isConfigured() {
        return props.kakaoConfigured();
    }

    public TravelTime travelTime(Double fromLat, Double fromLng, Double toLat, Double toLng, int fallbackMinutes) {
        if (!isConfigured() || fromLat == null || fromLng == null || toLat == null || toLng == null) {
            return new TravelTime(fallbackMinutes, "사용자 설정 기본 이동시간", false);
        }
        try {
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/v1/directions")
                            .queryParam("origin", fromLng + "," + fromLat)
                            .queryParam("destination", toLng + "," + toLat)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + props.kakaoRestKey())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode route = body == null ? null : body.path("routes").path(0);
            if (route == null || route.path("result_code").asInt(-1) != 0) {
                throw new IllegalStateException("경로 없음: " + (route == null ? "empty" : route.path("result_msg").asText()));
            }
            int seconds = route.path("summary").path("duration").asInt();
            return new TravelTime((int) Math.ceil(seconds / 60.0), "카카오모빌리티 길찾기(자동차)", true);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("이동시간 API 실패 → 기본값 사용: {}", e.getMessage());
            return new TravelTime(fallbackMinutes, "사용자 설정 기본 이동시간 (API 실패)", false);
        }
    }
}
