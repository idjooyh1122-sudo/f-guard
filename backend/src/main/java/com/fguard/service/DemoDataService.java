package com.fguard.service;

import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.domain.AssignmentStatus;
import com.fguard.domain.AttendanceStatus;
import com.fguard.domain.ComponentType;
import com.fguard.entity.*;
import com.fguard.repository.AssignmentRepository;
import com.fguard.repository.AttendanceRecordRepository;
import com.fguard.repository.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/** 시연용 샘플 데이터. 과목이 하나도 없을 때만 생성한다 (기존 데이터를 지우지 않음). */
@Service
public class DemoDataService {
    private final CourseRepository courses;
    private final AttendanceRecordRepository attendance;
    private final AssignmentRepository assignments;
    private final CurrentUser currentUser;
    private final Clock clock;

    public DemoDataService(CourseRepository courses, AttendanceRecordRepository attendance,
                           AssignmentRepository assignments, CurrentUser currentUser, Clock clock) {
        this.courses = courses;
        this.attendance = attendance;
        this.assignments = assignments;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public int seed() {
        if (!courses.findByUserIdOrderByIdAsc(currentUser.id()).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_HAS_DATA", "이미 과목이 있어 샘플 데이터를 만들지 않았습니다.");
        }
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 1) 위험 과목: 결석 누적 + 과제 미제출
        Course ds = course("[샘플] 자료구조", "김교수", 30, 8, 3, "10", null, "60", null, true);
        ds.replaceSchedules(List.of(
                new ClassSchedule(1, LocalTime.of(9, 0), LocalTime.of(10, 15), "IT1호관 201"),
                new ClassSchedule(today.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(10, 15), "IT1호관 201")));
        ds.replaceComponents(List.of(
                new GradeComponent(ComponentType.ATTENDANCE, "출석", bd("10"), null),
                new GradeComponent(ComponentType.ASSIGNMENT, "과제", bd("20"), null),
                new GradeComponent(ComponentType.MIDTERM, "중간고사", bd("30"), bd("35")),
                new GradeComponent(ComponentType.FINAL, "기말고사", bd("40"), null)));
        ds.setRulesNote("[샘플] 결석 1회당 총점 1점 감점, 지각 3회=결석 1회, 1/4 이상 결석 시 F, 총점 60점 미만 F");
        ds = courses.save(ds);
        records(ds.getId(), today, new AttendanceStatus[]{
                AttendanceStatus.ATTENDED, AttendanceStatus.ABSENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT,
                AttendanceStatus.ATTENDED, AttendanceStatus.LATE, AttendanceStatus.ABSENT, AttendanceStatus.ATTENDED,
                AttendanceStatus.ABSENT, AttendanceStatus.ATTENDED});
        assignment(ds.getId(), "과제 1: 배열과 연결 리스트", now.minusDays(20), AssignmentStatus.SUBMITTED, "80");
        assignment(ds.getId(), "과제 2: 스택/큐 구현", now.minusDays(6), AssignmentStatus.MISSED, null);
        assignment(ds.getId(), "과제 3: 이진 탐색 트리", now.plusDays(3).withHour(23).withMinute(59), AssignmentStatus.PENDING, null);

        // 2) 안정 과목
        Course dm = course("[샘플] 이산수학", "이교수", 30, 8, null, "5", "2", "60", null, true);
        dm.replaceSchedules(List.of(
                new ClassSchedule(2, LocalTime.of(10, 30), LocalTime.of(11, 45), "자연과학대 1호관 105"),
                new ClassSchedule(today.getDayOfWeek().getValue(), LocalTime.of(13, 0), LocalTime.of(14, 15), "자연과학대 1호관 105")));
        dm.replaceComponents(List.of(
                new GradeComponent(ComponentType.ATTENDANCE, "출석", bd("10"), null),
                new GradeComponent(ComponentType.QUIZ, "퀴즈", bd("20"), bd("85")),
                new GradeComponent(ComponentType.MIDTERM, "중간고사", bd("35"), bd("88")),
                new GradeComponent(ComponentType.FINAL, "기말고사", bd("35"), null)));
        dm = courses.save(dm);
        records(dm.getId(), today, new AttendanceStatus[]{
                AttendanceStatus.ATTENDED, AttendanceStatus.ATTENDED, AttendanceStatus.LATE, AttendanceStatus.ATTENDED,
                AttendanceStatus.ATTENDED, AttendanceStatus.ATTENDED});
        assignment(dm.getId(), "증명 연습 과제", now.plusDays(9).withHour(18).withMinute(0), AssignmentStatus.PENDING, null);

        // 3) 규칙 미확인 과목 (계산 불가 예시)
        Course en = course("[샘플] 대학영어", null, null, null, null, null, null, null, null, false);
        en.replaceSchedules(List.of(new ClassSchedule(5, LocalTime.of(15, 0), LocalTime.of(16, 15), "교양관 302")));
        en.replaceComponents(List.of(
                new GradeComponent(ComponentType.ATTENDANCE, "출석", bd("20"), null),
                new GradeComponent(ComponentType.MIDTERM, "중간고사", bd("40"), null)));
        en.setRulesNote("[샘플] 강의계획서 미업로드 — 기말 비율, F 기준 미확인");
        courses.save(en);
        return 3;
    }

    private Course course(String name, String prof, Integer sessions, Integer absFail, Integer latesPerAbs,
                          String absPenalty, String latePenalty, String cutoff, Integer missFail, boolean confirmed) {
        Course c = new Course();
        c.setUserId(currentUser.id());
        c.setName(name);
        c.setProfessor(prof);
        c.setCredits(3);
        c.setTotalSessions(sessions);
        c.setAbsenceFailThreshold(absFail);
        c.setLatesPerAbsence(latesPerAbs);
        c.setAbsencePenalty(absPenalty == null ? null : bd(absPenalty));
        c.setLatePenalty(latePenalty == null ? null : bd(latePenalty));
        c.setFailCutoff(cutoff == null ? null : bd(cutoff));
        c.setMissingAssignmentFailCount(missFail);
        c.setRulesConfirmed(confirmed);
        return c;
    }

    private void records(Long courseId, LocalDate today, AttendanceStatus[] statuses) {
        for (int i = 0; i < statuses.length; i++) {
            AttendanceRecord r = new AttendanceRecord();
            r.setCourseId(courseId);
            r.setClassDate(today.minusDays(3L * (statuses.length - i)));
            r.setStatus(statuses[i]);
            attendance.save(r);
        }
    }

    private void assignment(Long courseId, String title, OffsetDateTime due, AssignmentStatus status, String score) {
        Assignment a = new Assignment();
        a.setCourseId(courseId);
        a.setTitle(title);
        a.setDueAt(due);
        a.setStatus(status);
        a.setScore(score == null ? null : bd(score));
        assignments.save(a);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
