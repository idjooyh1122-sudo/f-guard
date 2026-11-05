package com.fguard.service;

import com.fguard.client.GoogleCalendarClient;
import com.fguard.client.OpenAiClient;
import com.fguard.client.TravelTimeClient;
import com.fguard.client.WeatherClient;
import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.dto.SettingsDtos.*;
import com.fguard.entity.AppUser;
import com.fguard.repository.AppUserRepository;
import com.fguard.repository.GoogleCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {
    private final AppUserRepository users;
    private final GoogleCredentialRepository credentials;
    private final CurrentUser currentUser;
    private final OpenAiClient openAi;
    private final WeatherClient weather;
    private final TravelTimeClient travel;
    private final GoogleCalendarClient google;

    public SettingsService(AppUserRepository users, GoogleCredentialRepository credentials, CurrentUser currentUser,
                           OpenAiClient openAi, WeatherClient weather, TravelTimeClient travel, GoogleCalendarClient google) {
        this.users = users;
        this.credentials = credentials;
        this.currentUser = currentUser;
        this.openAi = openAi;
        this.weather = weather;
        this.travel = travel;
        this.google = google;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return toResponse(user());
    }

    public SettingsResponse update(SettingsRequest r) {
        AppUser u = user();
        u.setDisplayName(r.displayName().trim());
        u.setHomeLabel(r.homeLabel());
        u.setHomeLat(r.homeLat());
        u.setHomeLng(r.homeLng());
        u.setCampusLabel(r.campusLabel());
        u.setCampusLat(r.campusLat());
        u.setCampusLng(r.campusLng());
        u.setPrepMinutes(r.prepMinutes());
        u.setBaseTravelMinutes(r.baseTravelMinutes());
        u.setBufferMinutes(r.bufferMinutes());
        return toResponse(users.save(u));
    }

    @Transactional(readOnly = true)
    public IntegrationStatus integrations() {
        boolean googleConnected = credentials.findByUserId(currentUser.id()).isPresent();
        return new IntegrationStatus(
                new Item(openAi.isConfigured(), false, openAi.isConfigured() ? "REAL" : "MOCK",
                        openAi.isConfigured() ? "OpenAI GPT API 사용" : "OPENAI_API_KEY / 모델 미설정 → MOCK 응답"),
                new Item(weather.isConfigured(), false, weather.isConfigured() ? "REAL" : "MOCK",
                        weather.isConfigured() ? "기상청 단기예보 (호출 실패 시 MOCK 대체)" : "KMA_SERVICE_KEY 미설정 → MOCK 날씨"),
                new Item(travel.isConfigured(), false, travel.isConfigured() ? "REAL" : "MANUAL",
                        travel.isConfigured() ? "카카오모빌리티 자동차 경로" : "설정한 기본 이동시간 사용"),
                new Item(google.isConfigured(), googleConnected, google.isConfigured() ? "REAL" : "NOT_CONFIGURED",
                        google.isConfigured() ? (googleConnected ? "Google 계정 연동됨" : "연동 대기") : "GOOGLE_CLIENT_ID/SECRET 미설정 (.ics 내보내기 사용 가능)"));
    }

    private AppUser user() {
        return users.findById(currentUser.id()).orElseThrow(() -> ApiException.notFound("사용자"));
    }

    private static SettingsResponse toResponse(AppUser u) {
        return new SettingsResponse(u.getDisplayName(), u.getHomeLabel(), u.getHomeLat(), u.getHomeLng(),
                u.getCampusLabel(), u.getCampusLat(), u.getCampusLng(), u.getPrepMinutes(), u.getBaseTravelMinutes(),
                u.getBufferMinutes());
    }
}
