package com.fguard.controller;

import com.fguard.dto.AiDtos.*;
import com.fguard.service.ExcuseService;
import com.fguard.service.SyllabusService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AiController {
    private final SyllabusService syllabus;
    private final ExcuseService excuse;

    public AiController(SyllabusService syllabus, ExcuseService excuse) {
        this.syllabus = syllabus;
        this.excuse = excuse;
    }

    @PostMapping(value = "/syllabus/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SyllabusAnalysisResponse analyze(@RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) Long courseId) {
        return syllabus.analyze(file, courseId);
    }

    @GetMapping("/syllabus")
    public List<SyllabusAnalysisResponse> list() { return syllabus.list(); }

    @GetMapping("/syllabus/{id}")
    public SyllabusAnalysisResponse get(@PathVariable Long id) { return syllabus.get(id); }

    @PostMapping("/syllabus/{id}/confirm")
    public SyllabusAnalysisResponse confirm(@PathVariable Long id, @Valid @RequestBody SyllabusConfirmRequest req) {
        return syllabus.confirm(id, req);
    }

    @PostMapping("/excuse")
    public ExcuseResponse excuse(@Valid @RequestBody ExcuseRequest req) { return excuse.generate(req); }
}
