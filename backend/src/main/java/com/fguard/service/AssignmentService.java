package com.fguard.service;

import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.dto.RecordDtos.*;
import com.fguard.entity.Assignment;
import com.fguard.entity.CalendarSync;
import com.fguard.entity.Course;
import com.fguard.repository.AssignmentRepository;
import com.fguard.repository.CalendarSyncRepository;
import com.fguard.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssignmentService {
    private final AssignmentRepository assignments;
    private final CalendarSyncRepository syncs;
    private final CourseRepository courses;
    private final CourseService courseService;
    private final CurrentUser currentUser;

    public AssignmentService(AssignmentRepository assignments, CalendarSyncRepository syncs, CourseRepository courses,
                             CourseService courseService, CurrentUser currentUser) {
        this.assignments = assignments;
        this.syncs = syncs;
        this.courses = courses;
        this.courseService = courseService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listAll() {
        Map<Long, Course> byId = courses.findByUserIdOrderByIdAsc(currentUser.id()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        if (byId.isEmpty()) return List.of();
        List<Assignment> list = assignments.findByCourseIdInOrderByDueAtAsc(List.copyOf(byId.keySet()));
        return toResponses(list, byId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(Long courseId) {
        Course c = courseService.require(courseId);
        return toResponses(assignments.findByCourseIdOrderByDueAtAsc(courseId), Map.of(c.getId(), c));
    }

    public AssignmentResponse create(Long courseId, AssignmentRequest req) {
        Course c = courseService.require(courseId);
        Assignment a = new Assignment();
        a.setCourseId(courseId);
        apply(a, req);
        return toResponses(List.of(assignments.save(a)), Map.of(c.getId(), c)).getFirst();
    }

    public AssignmentResponse update(Long assignmentId, AssignmentRequest req) {
        Assignment a = require(assignmentId);
        Course c = courseService.require(a.getCourseId());
        apply(a, req);
        return toResponses(List.of(assignments.save(a)), Map.of(c.getId(), c)).getFirst();
    }

    public void delete(Long assignmentId) {
        assignments.delete(require(assignmentId));
    }

    public Assignment require(Long assignmentId) {
        Assignment a = assignments.findById(assignmentId).orElseThrow(() -> ApiException.notFound("과제"));
        courseService.require(a.getCourseId()); // 소유권 확인
        return a;
    }

    private static void apply(Assignment a, AssignmentRequest r) {
        BigDecimal max = r.maxScore() == null ? BigDecimal.valueOf(100) : r.maxScore();
        if (r.score() != null && r.score().compareTo(max) > 0) {
            throw ApiException.badRequest("INVALID_SCORE", "과제 점수는 만점을 넘을 수 없습니다.");
        }
        a.setTitle(r.title().trim());
        a.setDueAt(r.dueAt());
        a.setMaxScore(max);
        a.setScore(r.score());
        a.setStatus(r.status());
        if (r.confirmed() != null) a.setConfirmed(r.confirmed());
    }

    private List<AssignmentResponse> toResponses(List<Assignment> list, Map<Long, Course> coursesById) {
        Map<Long, CalendarSync> syncByAssignment = list.isEmpty() ? Map.of()
                : syncs.findByAssignmentIdIn(list.stream().map(Assignment::getId).toList()).stream()
                .collect(Collectors.toMap(CalendarSync::getAssignmentId, Function.identity()));
        return list.stream().map(a -> {
            CalendarSync s = syncByAssignment.get(a.getId());
            CalendarState cal = s == null ? new CalendarState(false, null, null, false)
                    : new CalendarState(true, s.getGoogleEventId(), s.getHtmlLink(),
                    a.getDueAt() != null && !a.getDueAt().toInstant().equals(s.getSyncedDueAt().toInstant()));
            Course c = coursesById.get(a.getCourseId());
            return new AssignmentResponse(a.getId(), a.getCourseId(), c == null ? null : c.getName(), a.getTitle(),
                    a.getDueAt(), a.getMaxScore(), a.getScore(), a.getStatus(), a.getSource(), a.isConfirmed(), cal);
        }).toList();
    }
}
