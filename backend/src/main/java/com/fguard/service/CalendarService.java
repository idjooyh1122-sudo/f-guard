package com.fguard.service;

import com.fguard.client.GoogleCalendarClient;
import com.fguard.client.GoogleCalendarClient.EventSpec;
import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.config.FGuardProperties;
import com.fguard.entity.Assignment;
import com.fguard.entity.CalendarSync;
import com.fguard.entity.Course;
import com.fguard.entity.GoogleCredential;
import com.fguard.repository.AssignmentRepository;
import com.fguard.repository.CalendarSyncRepository;
import com.fguard.repository.CourseRepository;
import com.fguard.repository.GoogleCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CalendarService {
    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
    /** D-3, D-1 알림 (분) */
    static final int[] REMINDERS = {3 * 24 * 60, 24 * 60};

    public enum SyncOutcome { CREATED, UPDATED, SKIPPED_DUPLICATE, SKIPPED_NO_DUE_DATE, SKIPPED_UNCONFIRMED, FAILED }

    public record SyncItemResult(Long assignmentId, String title, SyncOutcome outcome, String eventId, String htmlLink,
                                 boolean verified, String error) {}

    public record GoogleStatus(boolean configured, boolean connected, OffsetDateTime connectedAt) {}

    private final GoogleCalendarClient google;
    private final GoogleCredentialRepository credentials;
    private final CalendarSyncRepository syncs;
    private final AssignmentRepository assignments;
    private final CourseRepository courses;
    private final AssignmentService assignmentService;
    private final CurrentUser currentUser;
    private final FGuardProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> pendingStates = new ConcurrentHashMap<>();

    public CalendarService(GoogleCalendarClient google, GoogleCredentialRepository credentials, CalendarSyncRepository syncs,
                           AssignmentRepository assignments, CourseRepository courses, AssignmentService assignmentService,
                           CurrentUser currentUser, FGuardProperties props, Clock clock) {
        this.google = google;
        this.credentials = credentials;
        this.syncs = syncs;
        this.assignments = assignments;
        this.courses = courses;
        this.assignmentService = assignmentService;
        this.currentUser = currentUser;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GoogleStatus status() {
        Optional<GoogleCredential> c = credentials.findByUserId(currentUser.id());
        return new GoogleStatus(google.isConfigured(), c.isPresent(), c.map(GoogleCredential::getUpdatedAt).orElse(null));
    }

    public String authorizationUrl() {
        requireConfigured();
        byte[] buf = new byte[16];
        random.nextBytes(buf);
        String state = HexFormat.of().formatHex(buf);
        Instant now = Instant.now(clock);
        pendingStates.entrySet().removeIf(e -> e.getValue().isBefore(now));
        pendingStates.put(state, now.plus(Duration.ofMinutes(10)));
        return google.authorizationUrl(state);
    }

    /** OAuth 콜백 처리. 성공/실패와 관계없이 프론트엔드로 돌아갈 URL 을 반환한다. */
    @Transactional
    public String handleCallback(String code, String state, String error) {
        String back = props.frontendUrl() + "/calendar";
        if (error != null) return back + "?google=denied";
        Instant exp = state == null ? null : pendingStates.remove(state);
        if (exp == null || exp.isBefore(Instant.now(clock))) return back + "?google=invalid_state";
        try {
            GoogleCalendarClient.TokenResponse t = google.exchangeCode(code);
            GoogleCredential c = credentials.findByUserId(currentUser.id()).orElseGet(GoogleCredential::new);
            c.setUserId(currentUser.id());
            c.setAccessToken(t.accessToken());
            if (t.refreshToken() != null) c.setRefreshToken(t.refreshToken());
            c.setExpiresAt(OffsetDateTime.now(clock).plusSeconds(t.expiresIn()));
            c.setScope(t.scope());
            c.setUpdatedAt(OffsetDateTime.now(clock));
            credentials.save(c);
            return back + "?google=connected";
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Google 토큰 교환 실패: {}", e.getClass().getSimpleName());
            return back + "?google=token_failed";
        }
    }

    @Transactional
    public void disconnect() {
        credentials.findByUserId(currentUser.id()).ifPresent(credentials::delete);
    }

    /** 선택한 과제를 Google Calendar 에 등록. 이미 같은 마감일로 등록된 과제는 건너뛴다(중복 방지). */
    @Transactional
    public List<SyncItemResult> sync(List<Long> assignmentIds) {
        requireConfigured();
        GoogleCredential cred = credentials.findByUserId(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_NOT_CONNECTED", "Google 계정을 먼저 연동해 주세요."));
        String token = validAccessToken(cred);
        ZoneId zone = ZoneId.of(props.timezone());
        List<SyncItemResult> results = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(assignmentIds)) {
            Assignment a = assignmentService.require(id);
            if (!a.isConfirmed()) { results.add(skip(a, SyncOutcome.SKIPPED_UNCONFIRMED)); continue; }
            if (a.getDueAt() == null) { results.add(skip(a, SyncOutcome.SKIPPED_NO_DUE_DATE)); continue; }
            Course course = courses.findById(a.getCourseId()).orElseThrow();
            Optional<CalendarSync> existing = syncs.findByAssignmentId(a.getId());
            try {
                EventSpec spec = spec(a, course, zone);
                SyncOutcome outcome;
                GoogleCalendarClient.EventResult ev;
                if (existing.isPresent() && existing.get().getSyncedDueAt().toInstant().equals(a.getDueAt().toInstant())) {
                    Optional<GoogleCalendarClient.EventResult> still = google.get(token, existing.get().getGoogleEventId());
                    if (still.isPresent()) {
                        results.add(new SyncItemResult(a.getId(), a.getTitle(), SyncOutcome.SKIPPED_DUPLICATE,
                                still.get().id(), still.get().htmlLink(), true, null));
                        continue;
                    }
                    ev = google.insertOrGet(token, spec); // 사용자가 캘린더에서 삭제한 경우 재등록
                    outcome = SyncOutcome.CREATED;
                } else if (existing.isPresent()) {
                    ev = google.update(token, existing.get().getGoogleEventId(), spec);
                    outcome = SyncOutcome.UPDATED;
                } else {
                    ev = google.insertOrGet(token, spec);
                    outcome = SyncOutcome.CREATED;
                }
                boolean verified = google.get(token, ev.id()).isPresent();
                CalendarSync s = existing.orElseGet(CalendarSync::new);
                s.setAssignmentId(a.getId());
                s.setGoogleEventId(ev.id());
                s.setHtmlLink(ev.htmlLink());
                s.setSyncedDueAt(a.getDueAt());
                s.setSyncedAt(OffsetDateTime.now(clock));
                syncs.save(s);
                results.add(new SyncItemResult(a.getId(), a.getTitle(), outcome, ev.id(), ev.htmlLink(), verified,
                        verified ? null : "등록 후 조회에 실패했습니다."));
            } catch (RestClientException | IllegalStateException e) {
                log.warn("캘린더 등록 실패 assignment={} : {}", a.getId(), e.getMessage());
                results.add(new SyncItemResult(a.getId(), a.getTitle(), SyncOutcome.FAILED, null, null, false, e.getMessage()));
            }
        }
        return results;
    }

    /** Google 연동 없이도 쓸 수 있는 iCalendar(.ics) 내보내기 — D-3, D-1 알림 포함 */
    @Transactional(readOnly = true)
    public String exportIcs() {
        DateTimeFormatter utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        Map<Long, Course> byId = new HashMap<>();
        courses.findByUserIdOrderByIdAsc(currentUser.id()).forEach(c -> byId.put(c.getId(), c));
        StringBuilder sb = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//F-Guard//KO\r\nCALSCALE:GREGORIAN\r\n");
        String stamp = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(utc);
        if (!byId.isEmpty()) {
            for (Assignment a : assignments.findByCourseIdInOrderByDueAtAsc(List.copyOf(byId.keySet()))) {
                if (a.getDueAt() == null || !a.isConfirmed()) continue;
                OffsetDateTime due = a.getDueAt().withOffsetSameInstant(ZoneOffset.UTC);
                sb.append("BEGIN:VEVENT\r\n")
                        .append("UID:fguard-assignment-").append(a.getId()).append("@f-guard\r\n")
                        .append("DTSTAMP:").append(stamp).append("\r\n")
                        .append("DTSTART:").append(due.minusMinutes(30).format(utc)).append("\r\n")
                        .append("DTEND:").append(due.format(utc)).append("\r\n")
                        .append("SUMMARY:").append(ics("[마감] " + byId.get(a.getCourseId()).getName() + " - " + a.getTitle())).append("\r\n");
                for (int m : REMINDERS) {
                    sb.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:").append(ics(a.getTitle()))
                            .append("\r\nTRIGGER:-PT").append(m).append("M\r\nEND:VALARM\r\n");
                }
                sb.append("END:VEVENT\r\n");
            }
        }
        return sb.append("END:VCALENDAR\r\n").toString();
    }

    static String eventId(long userId, long assignmentId) {
        // Google 이벤트 ID 규칙: base32hex 문자(a-v, 0-9), 5~1024자
        return "fguardu" + userId + "a" + assignmentId;
    }

    private EventSpec spec(Assignment a, Course c, ZoneId zone) {
        OffsetDateTime due = a.getDueAt().atZoneSameInstant(zone).toOffsetDateTime();
        return new EventSpec(eventId(currentUser.id(), a.getId()),
                "[마감] " + c.getName() + " - " + a.getTitle(),
                "F를 막아라에서 등록한 과제 마감 일정입니다.\n과목: " + c.getName(),
                due.minusMinutes(30), due, zone.getId(), REMINDERS, a.getId());
    }

    private String validAccessToken(GoogleCredential c) {
        if (c.getExpiresAt().isAfter(OffsetDateTime.now(clock).plusMinutes(1))) return c.getAccessToken();
        if (c.getRefreshToken() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_EXPIRED", "Google 연동이 만료되었습니다. 다시 연동해 주세요.");
        }
        try {
            GoogleCalendarClient.TokenResponse t = google.refresh(c.getRefreshToken());
            c.setAccessToken(t.accessToken());
            c.setExpiresAt(OffsetDateTime.now(clock).plusSeconds(t.expiresIn()));
            c.setUpdatedAt(OffsetDateTime.now(clock));
            credentials.save(c);
            return t.accessToken();
        } catch (RestClientException | IllegalStateException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_EXPIRED", "Google 연동이 만료되었습니다. 다시 연동해 주세요.");
        }
    }

    private void requireConfigured() {
        if (!google.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Google OAuth 클라이언트가 설정되지 않았습니다 (.env 의 GOOGLE_CLIENT_ID/SECRET).");
        }
    }

    private static SyncItemResult skip(Assignment a, SyncOutcome o) {
        return new SyncItemResult(a.getId(), a.getTitle(), o, null, null, false, null);
    }

    private static String ics(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }
}
