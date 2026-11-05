package com.fguard.dto;

import com.fguard.domain.GradeDiagnosis;
import com.fguard.domain.LatenessRisk;

import java.time.LocalDate;
import java.util.List;

public final class DiagnosisDtos {
    private DiagnosisDtos() {}

    public record WhatIfRequest(int extraAbsences, int extraLates, List<Long> missAssignmentIds) {}

    public record CourseDiagnosis(Long courseId, String courseName, GradeDiagnosis diagnosis) {}

    public record LatenessResponse(Long courseId, String courseName, LocalDate date, String room, LatenessRisk risk) {}
}
