package com.fguard.domain;

import java.math.BigDecimal;
import java.util.List;

public record GradeDiagnosis(
        RiskStatus status,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        BigDecimal failCutoff,
        /** F 기준을 넘기 위해 남은 평가에서 확보해야 하는 비율(%) — 남은 평가 만점 대비 */
        BigDecimal requiredPercentOfRemaining,
        int effectiveAbsences,
        Integer remainingAbsencesBeforeF,
        int missedAssignments,
        List<ComponentBreakdown> components,
        List<String> reasons,
        List<String> warnings) {

    public enum RiskStatus {
        SAFE, LOW, MEDIUM, HIGH, F_CONFIRMED, CANNOT_CALCULATE;

        public boolean atLeast(RiskStatus other) { return ordinal() >= other.ordinal(); }
    }

    public record ComponentBreakdown(String name, ComponentType type, BigDecimal weight,
                                     BigDecimal lowerScore, BigDecimal upperScore, String basis) {}
}
