package com.fguard.service;

import com.fguard.client.TravelTimeClient;
import com.fguard.client.WeatherClient;
import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.domain.AttendanceStatus;
import com.fguard.domain.LatenessRisk;
import com.fguard.domain.WeatherSnapshot;
import com.fguard.dto.AiDtos.Explanation;
import com.fguard.dto.DiagnosisDtos.LatenessResponse;
import com.fguard.entity.AppUser;
import com.fguard.entity.AttendanceRecord;
import com.fguard.entity.ClassSchedule;
import com.fguard.entity.Course;
import com.fguard.repository.AppUserRepository;
import com.fguard.repository.AttendanceRecordRepository;
import com.fguard.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LatenessService {
    private final CourseRepository courses;
    private final AppUserRepository users;
    private final AttendanceRecordRepository attendance;
    private final WeatherClient weather;
    private final TravelTimeClient travel;
    private final LatenessRiskCalculator calculator;
    private final AiService ai;
    private final CurrentUser currentUser;
    private final Clock clock;

    public LatenessService(CourseRepository courses, AppUserRepository users, AttendanceRecordRepository attendance,
                           WeatherClient weather, TravelTimeClient travel, LatenessRiskCalculator calculator,
                           AiService ai, CurrentUser currentUser, Clock clock) {
        this.courses = courses;
        this.users = users;
        this.attendance = attendance;
        this.weather = weather;
        this.travel = travel;
        this.calculator = calculator;
        this.ai = ai;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record TodayClass(Course course, ClassSchedule schedule) {}

    /** 해당 날짜의 수업 목록 (시작 시간순) */
    public List<TodayClass> classesOn(LocalDate date) {
        int dow = date.getDayOfWeek().getValue();
        List<TodayClass> out = new ArrayList<>();
        for (Course c : courses.findByUserIdOrderByIdAsc(currentUser.id())) {
            for (ClassSchedule s : c.getSchedules()) {
                if (s.getDayOfWeek() == dow) out.add(new TodayClass(c, s));
            }
        }
        out.sort(Comparator.comparing(t -> t.schedule().getStartTime()));
        return out;
    }

    /**
     * 지정일(보통 오늘) 이후 가장 먼저 남아 있는 수업의 지각 위험.
     * 오늘 수업이 모두 끝났으면 다음 수업일(최대 7일 뒤)의 첫 수업을 반환한다.
     */
    public Optional<LatenessResponse> nextClassRisk(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < 7; i++) {
            LocalDate d = date.plusDays(i);
            Optional<TodayClass> first = classesOn(d).stream()
                    .filter(t -> d.atTime(t.schedule().getStartTime()).isAfter(now))
                    .findFirst();
            if (first.isPresent()) return Optional.of(evaluate(first.get(), d));
        }
        return Optional.empty();
    }

    public LatenessResponse riskFor(Long courseId, LocalDate date) {
        return classesOn(date).stream().filter(t -> t.course().getId().equals(courseId)).findFirst()
                .map(t -> evaluate(t, date))
                .orElseThrow(() -> ApiException.badRequest("NO_CLASS", "해당 날짜에 이 과목의 수업이 없습니다."));
    }

    public Explanation explain(LatenessResponse r) {
        List<String> reasons = new ArrayList<>();
        LatenessRisk risk = r.risk();
        reasons.add("지각 위험 점수는 %d점(%s)입니다.".formatted(risk.score(), levelKo(risk.level())));
        risk.factors().forEach(f -> reasons.add(f.description() + "."));
        reasons.add("권장 출발 시각은 %s, 준비 시작은 %s입니다.".formatted(
                risk.recommendedDeparture().toLocalTime(), risk.startPreparingBy().toLocalTime()));
        List<String> actions = new ArrayList<>();
        actions.add(risk.startPreparingBy().toLocalTime() + " 전에 준비를 시작하세요.");
        if (risk.weather() != null && risk.weather().precipType() != WeatherSnapshot.PrecipType.NONE) actions.add("우산과 여벌 신발을 챙기세요.");
        if (risk.expectedLateMinutes() != null) actions.add("지각이 예상되면 미리 교수님께 연락하고 지각 사유서를 준비하세요.");
        return ai.explain("오늘 '" + r.courseName() + "' 수업 지각 위험 계산 결과를 설명해 줘.", r, reasons, actions);
    }

    WeatherSnapshot weatherFor(LocalDateTime target) {
        AppUser u = user();
        double lat = Optional.ofNullable(u.getCampusLat()).orElse(37.8691);
        double lng = Optional.ofNullable(u.getCampusLng()).orElse(127.7437);
        return weather.forecast(lat, lng, target);
    }

    private LatenessResponse evaluate(TodayClass t, LocalDate date) {
        AppUser u = user();
        LocalDateTime start = date.atTime(t.schedule().getStartTime());
        WeatherSnapshot w = weatherFor(start);
        TravelTimeClient.TravelTime tt = travel.travelTime(u.getHomeLat(), u.getHomeLng(), u.getCampusLat(), u.getCampusLng(), u.getBaseTravelMinutes());

        List<Long> ids = courses.findByUserIdOrderByIdAsc(currentUser.id()).stream().map(Course::getId).toList();
        List<AttendanceRecord> recent = ids.isEmpty() ? List.of()
                : attendance.findByCourseIdInAndClassDateAfter(ids, LocalDate.now(clock).minusDays(30));
        int lates = (int) recent.stream().filter(r -> r.getStatus() == AttendanceStatus.LATE).count();

        LocalDateTime now = LocalDateTime.now(clock);
        LatenessRisk risk = calculator.evaluate(new LatenessRiskCalculator.Input(start,
                date.equals(now.toLocalDate()) ? now : null,
                u.getPrepMinutes(), tt.minutes(), u.getBufferMinutes(), w, lates, recent.size(), tt.source()));
        return new LatenessResponse(t.course().getId(), t.course().getName(), date, t.schedule().getRoom(), risk);
    }

    private AppUser user() {
        return users.findById(currentUser.id()).orElseThrow(() -> ApiException.notFound("사용자"));
    }

    static String levelKo(LatenessRisk.Level l) {
        return switch (l) { case LOW -> "낮음"; case MEDIUM -> "보통"; case HIGH -> "높음"; case CRITICAL -> "매우 높음"; };
    }
}
