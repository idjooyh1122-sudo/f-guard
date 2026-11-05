package com.fguard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fguard.config.FGuardProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Google OAuth 2.0 (웹 서버 앱 흐름) + Google Calendar API v3 events.
 * 액세스/리프레시 토큰은 로그에 기록하지 않는다.
 */
@Component
public class GoogleCalendarClient {
    public static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String scope) {}

    public record EventResult(String id, String htmlLink) {}

    public record EventSpec(String id, String summary, String description, OffsetDateTime start, OffsetDateTime end,
                            String timeZone, int[] reminderMinutes, long assignmentId) {}

    private final FGuardProperties.Google props;
    private final RestClient rest;
    private final ObjectMapper mapper;

    public GoogleCalendarClient(FGuardProperties properties, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = properties.google();
        this.rest = builder.build();
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return props.configured();
    }

    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    public TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");
        return token(form);
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return token(form);
    }

    private TokenResponse token(MultiValueMap<String, String> form) {
        JsonNode r = rest.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (r == null || !r.hasNonNull("access_token")) throw new IllegalStateException("Google 토큰 응답이 올바르지 않습니다.");
        return new TokenResponse(r.get("access_token").asText(),
                r.hasNonNull("refresh_token") ? r.get("refresh_token").asText() : null,
                r.path("expires_in").asLong(3600), r.path("scope").asText(null));
    }

    /** 이벤트 생성. 같은 id 의 이벤트가 이미 있으면(409) 기존 이벤트를 조회해 반환한다. */
    public EventResult insertOrGet(String accessToken, EventSpec spec) {
        try {
            JsonNode r = rest.post().uri(EVENTS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(eventBody(spec, true))
                    .retrieve()
                    .body(JsonNode.class);
            return toResult(r);
        } catch (HttpClientErrorException.Conflict e) {
            EventResult existing = get(accessToken, spec.id())
                    .orElseThrow(() -> new IllegalStateException("이벤트 ID 충돌 후 조회 실패"));
            return update(accessToken, existing.id(), spec);
        }
    }

    public EventResult update(String accessToken, String eventId, EventSpec spec) {
        JsonNode r = rest.put().uri(EVENTS_URL + "/{id}", eventId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(eventBody(spec, false))
                .retrieve()
                .body(JsonNode.class);
        return toResult(r);
    }

    /** 등록 결과 검증용 조회. 404/410(삭제됨)이면 empty. */
    public Optional<EventResult> get(String accessToken, String eventId) {
        try {
            JsonNode r = rest.get().uri(EVENTS_URL + "/{id}", eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (r == null || "cancelled".equals(r.path("status").asText())) return Optional.empty();
            return Optional.of(toResult(r));
        } catch (HttpClientErrorException e) {
            HttpStatusCode s = e.getStatusCode();
            if (s.value() == 404 || s.value() == 410) return Optional.empty();
            throw e;
        }
    }

    ObjectNode eventBody(EventSpec spec, boolean includeId) {
        ObjectNode b = mapper.createObjectNode();
        if (includeId) b.put("id", spec.id());
        b.put("summary", spec.summary());
        b.put("description", spec.description());
        b.putObject("start").put("dateTime", spec.start().toString()).put("timeZone", spec.timeZone());
        b.putObject("end").put("dateTime", spec.end().toString()).put("timeZone", spec.timeZone());
        ObjectNode reminders = b.putObject("reminders");
        reminders.put("useDefault", false);
        ArrayNode overrides = reminders.putArray("overrides");
        for (int m : spec.reminderMinutes()) overrides.addObject().put("method", "popup").put("minutes", m);
        b.putObject("extendedProperties").putObject("private").put("fguardAssignmentId", String.valueOf(spec.assignmentId()));
        return b;
    }

    private static EventResult toResult(JsonNode r) {
        if (r == null || !r.hasNonNull("id")) throw new IllegalStateException("Google Calendar 응답에 이벤트 ID가 없습니다.");
        return new EventResult(r.get("id").asText(), r.path("htmlLink").asText(null));
    }
}
