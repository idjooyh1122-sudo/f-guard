package com.fguard.controller;

import com.fguard.dto.AiDtos.Explanation;
import com.fguard.dto.DiagnosisDtos.*;
import com.fguard.service.DiagnosisService;
import com.fguard.service.LatenessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DiagnosisController {
    private final DiagnosisService diagnosis;
    private final LatenessService lateness;
    private final Clock clock;

    public DiagnosisController(DiagnosisService diagnosis, LatenessService lateness, Clock clock) {
        this.diagnosis = diagnosis;
        this.lateness = lateness;
        this.clock = clock;
    }

    @GetMapping("/diagnosis")
    public List<CourseDiagnosis> all() { return diagnosis.diagnoseAll(); }

    @GetMapping("/courses/{id}/diagnosis")
    public CourseDiagnosis one(@PathVariable Long id) { return diagnosis.diagnose(id, null); }

    /** 가정 시뮬레이션: 결석/지각 추가, 특정 과제 미제출 */
    @PostMapping("/courses/{id}/diagnosis/what-if")
    public CourseDiagnosis whatIf(@PathVariable Long id, @RequestBody WhatIfRequest req) { return diagnosis.diagnose(id, req); }

    @PostMapping("/courses/{id}/diagnosis/explain")
    public Explanation explain(@PathVariable Long id, @RequestBody(required = false) WhatIfRequest req) {
        return diagnosis.explain(id, req);
    }

    /** 오늘(또는 지정일) 다음 수업의 지각 위험 */
    @GetMapping("/lateness/next")
    public ResponseEntity<LatenessResponse> next(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return lateness.nextClassRisk(date == null ? LocalDate.now(clock) : date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/courses/{id}/lateness")
    public LatenessResponse forCourse(@PathVariable Long id,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return lateness.riskFor(id, date == null ? LocalDate.now(clock) : date);
    }

    @PostMapping("/courses/{id}/lateness/explain")
    public Explanation explainLateness(@PathVariable Long id,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return lateness.explain(lateness.riskFor(id, date == null ? LocalDate.now(clock) : date));
    }
}
