package com.fguard.service;

import com.fguard.config.CurrentUser;
import com.fguard.domain.GradeDiagnosis;
import com.fguard.domain.GradeInputs;
import com.fguard.dto.AiDtos.Explanation;
import com.fguard.dto.DiagnosisDtos.CourseDiagnosis;
import com.fguard.dto.DiagnosisDtos.WhatIfRequest;
import com.fguard.dto.RecordDtos.AttendanceSummary;
import com.fguard.entity.Course;
import com.fguard.repository.AssignmentRepository;
import com.fguard.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DiagnosisService {
    private final CourseService courseService;
    private final CourseRepository courses;
    private final AssignmentRepository assignments;
    private final AttendanceService attendanceService;
    private final GradeRiskCalculator calculator;
    private final AiService ai;
    private final CurrentUser currentUser;

    public DiagnosisService(CourseService courseService, CourseRepository courses, AssignmentRepository assignments,
                            AttendanceService attendanceService, GradeRiskCalculator calculator, AiService ai,
                            CurrentUser currentUser) {
        this.courseService = courseService;
        this.courses = courses;
        this.assignments = assignments;
        this.attendanceService = attendanceService;
        this.calculator = calculator;
        this.ai = ai;
        this.currentUser = currentUser;
    }

    public CourseDiagnosis diagnose(Long courseId, WhatIfRequest whatIf) {
        return diagnose(courseService.require(courseId), whatIf);
    }

    public List<CourseDiagnosis> diagnoseAll() {
        return courses.findByUserIdOrderByIdAsc(currentUser.id()).stream().map(c -> diagnose(c, null)).toList();
    }

    public Explanation explain(Long courseId, WhatIfRequest whatIf) {
        CourseDiagnosis d = diagnose(courseId, whatIf);
        List<String> actions = new ArrayList<>();
        GradeDiagnosis g = d.diagnosis();
        if (g.remainingAbsencesBeforeF() != null && g.remainingAbsencesBeforeF() <= 2) actions.add("남은 수업은 결석하지 않도록 일정을 비워 두세요.");
        if (g.missedAssignments() > 0) actions.add("미제출 과제의 지각 제출 가능 여부를 교수님께 문의하세요.");
        if (g.status() == GradeDiagnosis.RiskStatus.CANNOT_CALCULATE) actions.add("강의계획서의 평가 비율과 F 기준을 입력하면 정확한 진단이 가능합니다.");
        if (actions.isEmpty()) actions.add("다가오는 과제 마감일을 캘린더에 등록해 두세요.");
        List<String> reasons = new ArrayList<>(g.reasons());
        reasons.addAll(g.warnings());
        return ai.explain("과목 '" + d.courseName() + "'의 F학점 위험 진단 결과를 설명해 줘.", d, reasons, actions);
    }

    CourseDiagnosis diagnose(Course c, WhatIfRequest whatIf) {
        GradeInputs.Rules rules = new GradeInputs.Rules(c.getTotalSessions(), c.getAbsenceFailThreshold(),
                c.getLatesPerAbsence(), c.getAbsencePenalty(), c.getLatePenalty(), c.getFailCutoff(),
                c.getMissingAssignmentFailCount(), c.isRulesConfirmed());
        List<GradeInputs.Component> comps = c.getComponents().stream()
                .map(x -> new GradeInputs.Component(x.getType(), x.getName(), x.getWeight(), x.getScore())).toList();
        List<GradeInputs.AssignmentItem> items = assignments.findByCourseIdOrderByDueAtAsc(c.getId()).stream()
                .filter(a -> a.isConfirmed())
                .map(a -> new GradeInputs.AssignmentItem(a.getId(), a.getTitle(), a.getDueAt(), a.getMaxScore(), a.getScore(), a.getStatus()))
                .toList();
        AttendanceSummary s = attendanceService.summary(c.getId());
        GradeInputs.WhatIf wi = whatIf == null ? GradeInputs.WhatIf.NONE
                : new GradeInputs.WhatIf(whatIf.extraAbsences(), whatIf.extraLates(),
                whatIf.missAssignmentIds() == null ? List.of() : whatIf.missAssignmentIds());
        GradeDiagnosis d = calculator.diagnose(rules, comps, items,
                new GradeInputs.Attendance(s.attended(), s.late(), s.absent(), s.excused()), wi);
        return new CourseDiagnosis(c.getId(), c.getName(), d);
    }
}
