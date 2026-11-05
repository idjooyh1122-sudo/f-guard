package com.fguard.controller;

import com.fguard.dto.CourseDtos.CourseRequest;
import com.fguard.dto.CourseDtos.CourseResponse;
import com.fguard.dto.RecordDtos.*;
import com.fguard.service.AssignmentService;
import com.fguard.service.AttendanceService;
import com.fguard.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseController {
    private final CourseService courses;
    private final AttendanceService attendance;
    private final AssignmentService assignments;

    public CourseController(CourseService courses, AttendanceService attendance, AssignmentService assignments) {
        this.courses = courses;
        this.attendance = attendance;
        this.assignments = assignments;
    }

    @GetMapping("/courses")
    public List<CourseResponse> list() { return courses.list(); }

    @GetMapping("/courses/{id}")
    public CourseResponse get(@PathVariable Long id) { return courses.get(id); }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CourseRequest req) { return courses.create(req); }

    @PutMapping("/courses/{id}")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody CourseRequest req) { return courses.update(id, req); }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { courses.delete(id); }

    // ---- 출석 ----
    @GetMapping("/courses/{id}/attendance")
    public List<AttendanceResponse> attendance(@PathVariable Long id) { return attendance.list(id); }

    @GetMapping("/courses/{id}/attendance/summary")
    public AttendanceSummary attendanceSummary(@PathVariable Long id) {
        courses.require(id);
        return attendance.summary(id);
    }

    @PutMapping("/courses/{id}/attendance")
    public AttendanceResponse upsertAttendance(@PathVariable Long id, @Valid @RequestBody AttendanceRequest req) {
        return attendance.upsert(id, req);
    }

    @DeleteMapping("/courses/{id}/attendance/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttendance(@PathVariable Long id, @PathVariable Long recordId) { attendance.delete(id, recordId); }

    // ---- 과제 ----
    @GetMapping("/assignments")
    public List<AssignmentResponse> allAssignments() { return assignments.listAll(); }

    @GetMapping("/courses/{id}/assignments")
    public List<AssignmentResponse> assignments(@PathVariable Long id) { return assignments.list(id); }

    @PostMapping("/courses/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse createAssignment(@PathVariable Long id, @Valid @RequestBody AssignmentRequest req) {
        return assignments.create(id, req);
    }

    @PutMapping("/assignments/{assignmentId}")
    public AssignmentResponse updateAssignment(@PathVariable Long assignmentId, @Valid @RequestBody AssignmentRequest req) {
        return assignments.update(assignmentId, req);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable Long assignmentId) { assignments.delete(assignmentId); }
}
