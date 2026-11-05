package com.fguard.service;

import com.fguard.domain.GradeDiagnosis.RiskStatus;
import com.fguard.dto.DiagnosisDtos.CourseDiagnosis;
import com.fguard.dto.DiagnosisDtos.LatenessResponse;
import com.fguard.dto.RecordDtos.AssignmentResponse;
import com.fguard.dto.SettingsDtos.IntegrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final LatenessService lateness;
    private final AssignmentService assignments;
    private final DiagnosisService diagnosis;
    private final SettingsService settings;
    private final Clock clock;

    public DashboardService(LatenessService lateness, AssignmentService assignments, DiagnosisService diagnosis,
                            SettingsService settings, Clock clock) {
        this.lateness = lateness;
        this.assignments = assignments;
        this.diagnosis = diagnosis;
        this.settings = settings;
        this.clock = clock;
    }

    public record TodayClassItem(Long courseId, String courseName, LocalTime startTime, LocalTime endTime, String room) {}

    public record Dashboard(LocalDate date, List<TodayClassItem> todayClasses, LatenessResponse nextClassRisk,
                            List<AssignmentResponse> upcomingAssignments, List<CourseDiagnosis> riskCourses,
                            int courseCount, IntegrationStatus integrations) {}

    public Dashboard get() {
        LocalDate today = LocalDate.now(clock);
        List<TodayClassItem> classes = lateness.classesOn(today).stream()
                .map(t -> new TodayClassItem(t.course().getId(), t.course().getName(), t.schedule().getStartTime(),
                        t.schedule().getEndTime(), t.schedule().getRoom()))
                .toList();
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<AssignmentResponse> upcoming = assignments.listAll().stream()
                .filter(a -> a.dueAt() != null && a.status() == com.fguard.domain.AssignmentStatus.PENDING)
                .filter(a -> a.dueAt().isAfter(now) && a.dueAt().isBefore(now.plusDays(14)))
                .limit(6)
                .toList();
        List<CourseDiagnosis> all = diagnosis.diagnoseAll();
        List<CourseDiagnosis> risky = all.stream()
                .sorted(Comparator.comparingInt((CourseDiagnosis d) -> severity(d.diagnosis().status())).reversed())
                .toList();
        return new Dashboard(today, classes, lateness.nextClassRisk(today).orElse(null), upcoming, risky, all.size(),
                settings.integrations());
    }

    private static int severity(RiskStatus s) {
        return switch (s) {
            case F_CONFIRMED -> 6; case HIGH -> 5; case MEDIUM -> 4; case CANNOT_CALCULATE -> 3; case LOW -> 2; case SAFE -> 1;
        };
    }
}
