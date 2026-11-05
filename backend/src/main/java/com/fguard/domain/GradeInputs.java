package com.fguard.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 성적 위험 계산기 입력 모델 (JPA 엔티티와 분리된 순수 값 객체). */
public final class GradeInputs {
    private GradeInputs() {}

    public record Rules(
            Integer totalSessions,
            Integer absenceFailThreshold,
            Integer latesPerAbsence,
            BigDecimal absencePenalty,
            BigDecimal latePenalty,
            BigDecimal failCutoff,
            Integer missingAssignmentFailCount,
            boolean rulesConfirmed) {}

    public record Component(ComponentType type, String name, BigDecimal weight, BigDecimal score) {}

    public record AssignmentItem(Long id, String title, OffsetDateTime dueAt, BigDecimal maxScore,
                                 BigDecimal score, AssignmentStatus status) {}

    public record Attendance(int attended, int late, int absent, int excused) {
        public int recordedSessions() { return attended + late + absent + excused; }
    }

    /** 가정 시뮬레이션: "이번 과제를 놓치면?" "한 번 더 결석하면?" */
    public record WhatIf(int extraAbsences, int extraLates, List<Long> missAssignmentIds) {
        public static final WhatIf NONE = new WhatIf(0, 0, List.of());
        public boolean isEmpty() {
            return extraAbsences == 0 && extraLates == 0 && (missAssignmentIds == null || missAssignmentIds.isEmpty());
        }
    }
}
