package com.fguard.service;

import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.dto.CourseDtos.*;
import com.fguard.entity.ClassSchedule;
import com.fguard.entity.Course;
import com.fguard.entity.GradeComponent;
import com.fguard.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CourseService {
    private final CourseRepository courses;
    private final CurrentUser currentUser;

    public CourseService(CourseRepository courses, CurrentUser currentUser) {
        this.courses = courses;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> list() {
        return courses.findByUserIdOrderByIdAsc(currentUser.id()).stream().map(CourseService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long id) {
        return toResponse(require(id));
    }

    public CourseResponse create(CourseRequest req) {
        Course c = new Course();
        c.setUserId(currentUser.id());
        apply(c, req);
        return toResponse(courses.save(c));
    }

    public CourseResponse update(Long id, CourseRequest req) {
        Course c = require(id);
        apply(c, req);
        return toResponse(courses.save(c));
    }

    public void delete(Long id) {
        courses.delete(require(id));
    }

    public Course require(Long id) {
        return courses.findByIdAndUserId(id, currentUser.id()).orElseThrow(() -> ApiException.notFound("과목"));
    }

    private static void apply(Course c, CourseRequest r) {
        if (r.schedules() != null) {
            for (ScheduleDto s : r.schedules()) {
                if (!s.endTime().isAfter(s.startTime())) {
                    throw ApiException.badRequest("INVALID_SCHEDULE", "수업 종료 시간은 시작 시간보다 늦어야 합니다.");
                }
            }
        }
        c.setName(r.name().trim());
        c.setProfessor(r.professor());
        c.setCredits(r.credits());
        c.setTotalSessions(r.totalSessions());
        c.setAbsenceFailThreshold(r.absenceFailThreshold());
        c.setLatesPerAbsence(r.latesPerAbsence());
        c.setAbsencePenalty(r.absencePenalty());
        c.setLatePenalty(r.latePenalty());
        c.setFailCutoff(r.failCutoff());
        c.setMissingAssignmentFailCount(r.missingAssignmentFailCount());
        c.setRulesNote(r.rulesNote());
        c.setRulesConfirmed(r.rulesConfirmed());
        if (r.schedules() != null) {
            c.replaceSchedules(r.schedules().stream()
                    .map(s -> new ClassSchedule(s.dayOfWeek(), s.startTime(), s.endTime(), s.room())).toList());
        }
        if (r.components() != null) {
            c.replaceComponents(r.components().stream()
                    .map(x -> new GradeComponent(x.type(), x.name().trim(), x.weight(), x.score())).toList());
        }
    }

    public static CourseResponse toResponse(Course c) {
        List<ScheduleDto> schedules = c.getSchedules().stream()
                .map(s -> new ScheduleDto(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getRoom())).toList();
        List<ComponentDto> comps = c.getComponents().stream()
                .map(x -> new ComponentDto(x.getId(), x.getType(), x.getName(), x.getWeight(), x.getScore())).toList();
        BigDecimal sum = c.getComponents().stream().map(GradeComponent::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CourseResponse(c.getId(), c.getName(), c.getProfessor(), c.getCredits(), c.getTotalSessions(),
                c.getAbsenceFailThreshold(), c.getLatesPerAbsence(), c.getAbsencePenalty(), c.getLatePenalty(),
                c.getFailCutoff(), c.getMissingAssignmentFailCount(), c.getRulesNote(), c.isRulesConfirmed(),
                schedules, comps, sum);
    }
}
