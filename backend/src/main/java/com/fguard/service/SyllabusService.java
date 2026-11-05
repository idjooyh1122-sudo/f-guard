package com.fguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fguard.client.OpenAiClient;
import com.fguard.config.ApiException;
import com.fguard.config.CurrentUser;
import com.fguard.config.FGuardProperties;
import com.fguard.domain.*;
import com.fguard.dto.AiDtos.SyllabusAnalysisResponse;
import com.fguard.dto.AiDtos.SyllabusConfirmRequest;
import com.fguard.entity.Assignment;
import com.fguard.entity.Course;
import com.fguard.entity.GradeComponent;
import com.fguard.entity.SyllabusAnalysis;
import com.fguard.repository.AssignmentRepository;
import com.fguard.repository.CourseRepository;
import com.fguard.repository.SyllabusAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SyllabusService {
    private static final Logger log = LoggerFactory.getLogger(SyllabusService.class);

    private final SyllabusAnalysisRepository analyses;
    private final CourseRepository courses;
    private final AssignmentRepository assignments;
    private final CourseService courseService;
    private final AiService ai;
    private final JsonSchemaValidator validator;
    private final ObjectMapper mapper;
    private final CurrentUser currentUser;
    private final FGuardProperties props;
    private final Clock clock;

    public SyllabusService(SyllabusAnalysisRepository analyses, CourseRepository courses, AssignmentRepository assignments,
                           CourseService courseService, AiService ai, JsonSchemaValidator validator, ObjectMapper mapper,
                           CurrentUser currentUser, FGuardProperties props, Clock clock) {
        this.analyses = analyses;
        this.courses = courses;
        this.assignments = assignments;
        this.courseService = courseService;
        this.ai = ai;
        this.validator = validator;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.props = props;
        this.clock = clock;
    }

    /** 업로드 파일 검증 → 분석 → 검토 대기 상태로 저장 */
    @Transactional
    public SyllabusAnalysisResponse analyze(MultipartFile file, Long courseId) {
        if (courseId != null) courseService.require(courseId);
        byte[] bytes = readAndValidate(file);
        String contentType = FileTypeDetector.detect(bytes)
                .orElseThrow(() -> ApiException.badRequest("UNSUPPORTED_FILE", "PDF, PNG, JPG, WEBP 파일만 업로드할 수 있습니다."));
        String fileName = Optional.ofNullable(file.getOriginalFilename()).filter(s -> !s.isBlank()).orElse("syllabus");

        SyllabusAnalysis a = new SyllabusAnalysis();
        a.setUserId(currentUser.id());
        a.setCourseId(courseId);
        a.setFileName(fileName.length() > 255 ? fileName.substring(0, 255) : fileName);
        a.setContentType(contentType);
        a.setFileSize(bytes.length);
        a.setProviderMode(ai.isReal() ? ProviderMode.REAL : ProviderMode.MOCK);
        try {
            AiService.SyllabusResult r = ai.analyzeSyllabus(fileName, contentType, bytes, LocalDate.now(clock).getYear());
            List<String> schemaErrors = validator.validate(JsonSchemaValidator.SYLLABUS, r.json());
            if (!schemaErrors.isEmpty()) {
                throw new OpenAiClient.OpenAiException("스키마 검증 실패: " + String.join("; ", schemaErrors));
            }
            a.setProviderMode(r.mode());
            a.setModel(r.model());
            a.setResultJson(mapper.writeValueAsString(r.json()));
            a.setStatus(AnalysisStatus.PENDING_REVIEW);
        } catch (OpenAiClient.OpenAiException e) {
            a.setStatus(AnalysisStatus.FAILED);
            a.setErrorMessage(e.getMessage());
        } catch (IOException e) {
            a.setStatus(AnalysisStatus.FAILED);
            a.setErrorMessage("분석 결과 저장 실패");
        }
        return toResponse(analyses.save(a));
    }

    @Transactional(readOnly = true)
    public List<SyllabusAnalysisResponse> list() {
        return analyses.findByUserIdOrderByIdDesc(currentUser.id()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SyllabusAnalysisResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 사용자가 검토·수정한 결과로 과목/평가 규칙/과제를 저장한다. */
    @Transactional
    public SyllabusAnalysisResponse confirm(Long id, SyllabusConfirmRequest req) {
        SyllabusAnalysis a = require(id);
        if (a.getStatus() == AnalysisStatus.FAILED) {
            throw ApiException.badRequest("ANALYSIS_FAILED", "실패한 분석은 확정할 수 없습니다. 다시 업로드해 주세요.");
        }
        JsonNode r = req.result();
        List<String> schemaErrors = validator.validate(JsonSchemaValidator.SYLLABUS, r);
        if (!schemaErrors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEMA_INVALID", "분석 결과 형식 오류: " + String.join("; ", schemaErrors));
        }
        List<String> hardErrors = semanticIssues(r).stream().filter(s -> s.startsWith("[오류]")).toList();
        if (!hardErrors.isEmpty()) {
            throw ApiException.badRequest("RESULT_INVALID", String.join(" ", hardErrors));
        }

        Long targetCourseId = req.courseId() != null ? req.courseId() : a.getCourseId();
        Course c = targetCourseId != null ? courseService.require(targetCourseId) : new Course();
        boolean isNew = c.getId() == null;
        if (isNew) c.setUserId(currentUser.id());

        String name = text(r, "courseName");
        if (isNew && name == null) throw ApiException.badRequest("NAME_REQUIRED", "과목명을 입력해 주세요.");
        if (name != null) c.setName(name);
        if (text(r, "professor") != null) c.setProfessor(text(r, "professor"));
        c.setTotalSessions(intOrNull(r.path("totalSessions")));
        JsonNode ar = r.path("attendanceRules");
        c.setAbsenceFailThreshold(intOrNull(ar.path("absenceFailThreshold")));
        c.setLatesPerAbsence(intOrNull(ar.path("latesPerAbsence")));
        c.setAbsencePenalty(decOrNull(ar.path("absencePenalty")));
        c.setLatePenalty(decOrNull(ar.path("latePenalty")));
        c.setMissingAssignmentFailCount(intOrNull(r.path("assignmentRules").path("missingAssignmentFailCount")));
        c.setFailCutoff(decOrNull(r.path("failCriteria").path("failCutoff")));
        c.setRulesNote(joinNonNull(" / ", text(ar, "description"), text(r.path("assignmentRules"), "latePolicy"),
                text(r.path("failCriteria"), "description")));
        c.setRulesConfirmed(true); // 사용자가 화면에서 검토 후 확정

        List<GradeComponent> comps = new ArrayList<>();
        JsonNode w = r.path("weights");
        addComponent(comps, w, "attendance", ComponentType.ATTENDANCE, "출석");
        addComponent(comps, w, "assignment", ComponentType.ASSIGNMENT, "과제");
        addComponent(comps, w, "midterm", ComponentType.MIDTERM, "중간고사");
        addComponent(comps, w, "final", ComponentType.FINAL, "기말고사");
        addComponent(comps, w, "quiz", ComponentType.QUIZ, "퀴즈");
        addComponent(comps, w, "other", ComponentType.OTHER, Optional.ofNullable(text(w, "otherLabel")).orElse("기타"));
        if (!comps.isEmpty()) c.replaceComponents(comps);
        c = courses.save(c);

        Set<String> existingTitles = new HashSet<>();
        assignments.findByCourseIdOrderByDueAtAsc(c.getId()).forEach(x -> existingTitles.add(x.getTitle()));
        ZoneId zone = ZoneId.of(props.timezone());
        for (JsonNode item : r.path("assignments")) {
            String title = text(item, "title");
            if (title == null || existingTitles.contains(title)) continue;
            Assignment as = new Assignment();
            as.setCourseId(c.getId());
            as.setTitle(title.length() > 150 ? title.substring(0, 150) : title);
            as.setSource(AssignmentSource.SYLLABUS);
            as.setConfirmed(true);
            String date = text(item, "dueDate");
            if (date != null) {
                LocalTime time = Optional.ofNullable(text(item, "dueTime")).map(LocalTime::parse).orElse(LocalTime.of(23, 59));
                as.setDueAt(LocalDate.parse(date).atTime(time).atZone(zone).toOffsetDateTime());
            }
            assignments.save(as);
            existingTitles.add(title);
        }

        try {
            a.setResultJson(mapper.writeValueAsString(r));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        a.setCourseId(c.getId());
        a.setStatus(AnalysisStatus.CONFIRMED);
        a.setConfirmedAt(OffsetDateTime.now(clock));
        log.info("강의계획서 분석 {} 확정 → 과목 {}", a.getId(), c.getId());
        return toResponse(analyses.save(a));
    }

    /** 스키마 외 의미 검증. [오류] 는 확정 불가, [확인] 은 사용자 확인 권장. */
    public static List<String> semanticIssues(JsonNode r) {
        List<String> issues = new ArrayList<>();
        if (r == null || r.isMissingNode() || r.isNull()) return issues;
        JsonNode w = r.path("weights");
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (String k : List.of("attendance", "assignment", "midterm", "final", "quiz", "other")) {
            JsonNode v = w.path(k);
            if (v.isNumber()) {
                any = true;
                if (v.decimalValue().compareTo(BigDecimal.ZERO) < 0 || v.decimalValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                    issues.add("[오류] 평가 비율 '" + k + "' 값이 0~100 범위를 벗어났습니다.");
                }
                sum = sum.add(v.decimalValue());
            }
        }
        if (!any) issues.add("[확인] 평가 비율을 문서에서 찾지 못했습니다.");
        else if (sum.compareTo(BigDecimal.valueOf(100)) != 0) issues.add("[확인] 평가 비율 합계가 " + sum.stripTrailingZeros().toPlainString() + "%입니다.");
        checkRange(issues, r.path("failCriteria").path("failCutoff"), "F 기준 점수", 0, 100);
        checkRange(issues, r.path("attendanceRules").path("absencePenalty"), "결석 감점", 0, 100);
        checkRange(issues, r.path("attendanceRules").path("latePenalty"), "지각 감점", 0, 100);
        checkRange(issues, r.path("attendanceRules").path("absenceFailThreshold"), "결석 F 기준", 1, 200);
        checkRange(issues, r.path("attendanceRules").path("latesPerAbsence"), "지각→결석 환산", 1, 50);
        checkRange(issues, r.path("totalSessions"), "전체 수업 횟수", 1, 200);
        int i = 0;
        for (JsonNode a : r.path("assignments")) {
            i++;
            String d = a.path("dueDate").isTextual() ? a.path("dueDate").asText() : null;
            String t = a.path("dueTime").isTextual() ? a.path("dueTime").asText() : null;
            if (d != null) {
                try { LocalDate.parse(d); } catch (DateTimeParseException e) { issues.add("[오류] 과제 " + i + " 마감일 형식(YYYY-MM-DD)이 올바르지 않습니다."); }
            } else {
                issues.add("[확인] 과제 " + i + "의 마감일이 문서에 없습니다. 캘린더 등록 전 직접 입력하세요.");
            }
            if (t != null) {
                try { LocalTime.parse(t); } catch (DateTimeParseException e) { issues.add("[오류] 과제 " + i + " 마감 시간 형식(HH:MM)이 올바르지 않습니다."); }
            }
            if (a.path("title").asText("").isBlank()) issues.add("[오류] 과제 " + i + "의 제목이 비어 있습니다.");
        }
        for (JsonNode u : r.path("uncertainFields")) issues.add("[확인] 불확실 항목: " + u.asText());
        return issues;
    }

    private static void checkRange(List<String> issues, JsonNode v, String label, double min, double max) {
        if (v.isNumber() && (v.asDouble() < min || v.asDouble() > max)) {
            issues.add("[오류] " + label + " 값이 허용 범위(" + (int) min + "~" + (int) max + ")를 벗어났습니다.");
        }
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "파일이 비어 있습니다.");
        if (file.getSize() > props.upload().maxBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "파일은 10MB 이하만 업로드할 수 있습니다.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("READ_FAILED", "파일을 읽을 수 없습니다.");
        }
    }

    private SyllabusAnalysis require(Long id) {
        return analyses.findByIdAndUserId(id, currentUser.id()).orElseThrow(() -> ApiException.notFound("분석 결과"));
    }

    private SyllabusAnalysisResponse toResponse(SyllabusAnalysis a) {
        JsonNode json = null;
        try {
            if (a.getResultJson() != null) json = mapper.readTree(a.getResultJson());
        } catch (IOException e) {
            log.warn("분석 결과 JSON 파싱 실패 id={}", a.getId());
        }
        return new SyllabusAnalysisResponse(a.getId(), a.getCourseId(), a.getFileName(), a.getFileSize(), a.getStatus(),
                a.getProviderMode(), a.getModel(), json, semanticIssues(json), a.getErrorMessage(),
                a.getCreatedAt(), a.getConfirmedAt());
    }

    private static void addComponent(List<GradeComponent> out, JsonNode w, String key, ComponentType type, String name) {
        JsonNode v = w.path(key);
        if (v.isNumber() && v.decimalValue().signum() > 0) out.add(new GradeComponent(type, name, v.decimalValue(), null));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().trim() : null;
    }

    private static Integer intOrNull(JsonNode v) { return v.isNumber() ? v.intValue() : null; }

    private static BigDecimal decOrNull(JsonNode v) { return v.isNumber() ? v.decimalValue() : null; }

    private static String joinNonNull(String sep, String... parts) {
        String s = String.join(sep, Arrays.stream(parts).filter(Objects::nonNull).toList());
        return s.isBlank() ? null : s;
    }
}
