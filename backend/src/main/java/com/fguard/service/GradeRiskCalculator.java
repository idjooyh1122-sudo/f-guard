package com.fguard.service;

import com.fguard.domain.AssignmentStatus;
import com.fguard.domain.ComponentType;
import com.fguard.domain.GradeDiagnosis;
import com.fguard.domain.GradeDiagnosis.ComponentBreakdown;
import com.fguard.domain.GradeDiagnosis.RiskStatus;
import com.fguard.domain.GradeInputs.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * F학점 위험 진단기. GPT 를 사용하지 않는 결정론적 계산이다.
 *
 * <p>핵심 아이디어: 평가 항목마다 "지금 확정된 최저 점수(lower)"와 "남은 평가를 모두 만점 받을 때의
 * 최고 점수(upper)"를 구해 총점의 구간 [lower, upper] 를 만든다.
 * <ul>
 *   <li>upper &lt; F 기준 → 남은 평가와 무관하게 F 확정 (규칙이 사용자 확인을 거친 경우에만)</li>
 *   <li>lower ≥ F 기준 → 안전</li>
 *   <li>그 사이 → 남은 평가에서 필요한 비율로 위험 단계 산정</li>
 * </ul>
 */
@org.springframework.stereotype.Component
public class GradeRiskCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");

    private final Clock clock;

    public GradeRiskCalculator(Clock clock) {
        this.clock = clock;
    }

    public GradeDiagnosis diagnose(Rules rules, List<Component> components, List<AssignmentItem> assignments,
                                   Attendance attendance, WhatIf whatIf) {
        WhatIf wi = whatIf == null ? WhatIf.NONE : whatIf;
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ---- 가정(what-if) 적용 ----
        int absent = attendance.absent() + Math.max(0, wi.extraAbsences());
        int late = attendance.late() + Math.max(0, wi.extraLates());
        int recorded = attendance.recordedSessions() + Math.max(0, wi.extraAbsences()) + Math.max(0, wi.extraLates());
        Set<Long> forcedMiss = wi.missAssignmentIds() == null ? Set.of() : new HashSet<>(wi.missAssignmentIds());
        List<AssignmentItem> effAssignments = assignments.stream()
                .map(a -> forcedMiss.contains(a.id())
                        ? new AssignmentItem(a.id(), a.title(), a.dueAt(), a.maxScore(), null, AssignmentStatus.MISSED)
                        : a)
                .toList();

        // ---- 출석: 지각 환산 ----
        int convertedFromLate = 0;
        int leftoverLates = late;
        if (rules.latesPerAbsence() != null && rules.latesPerAbsence() > 0) {
            convertedFromLate = late / rules.latesPerAbsence();
            leftoverLates = late % rules.latesPerAbsence();
        }
        int effectiveAbsences = absent + convertedFromLate;
        Integer remainingAbsences = null;
        boolean attendanceFail = false;
        if (rules.absenceFailThreshold() != null) {
            // 몇 번 더 결석하면 F 기준에 도달하는지
            remainingAbsences = Math.max(0, rules.absenceFailThreshold() - effectiveAbsences);
            if (effectiveAbsences >= rules.absenceFailThreshold()) {
                attendanceFail = true;
                reasons.add("환산 결석 %d회로 결석 F 기준(%d회 이상)에 도달했습니다.".formatted(effectiveAbsences, rules.absenceFailThreshold()));
            }
        } else {
            warnings.add("결석 F 기준이 확인되지 않았습니다. 강의계획서 또는 학칙을 확인해 입력하세요.");
        }

        // ---- 과제: 미제출 집계 ----
        int missed = (int) effAssignments.stream().filter(a -> isMissed(a, now)).count();
        boolean assignmentFail = false;
        if (rules.missingAssignmentFailCount() != null && missed >= rules.missingAssignmentFailCount()) {
            assignmentFail = true;
            reasons.add("과제 미제출 %d건으로 과제 F 기준(%d건 이상)에 도달했습니다.".formatted(missed, rules.missingAssignmentFailCount()));
        }

        // ---- 평가 항목별 점수 구간 ----
        List<ComponentBreakdown> breakdown = new ArrayList<>();
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        BigDecimal upper = BigDecimal.ZERO;
        for (Component c : components) {
            BigDecimal[] range;
            String basis;
            if (c.score() != null) {
                range = new BigDecimal[]{c.score(), c.score()};
                basis = "입력된 점수";
            } else if (c.type() == ComponentType.ATTENDANCE) {
                AttendanceRange ar = attendanceRange(rules, effectiveAbsences, leftoverLates, recorded);
                range = new BigDecimal[]{ar.lower, ar.upper};
                basis = ar.basis;
                if (ar.warning != null) warnings.add(ar.warning);
            } else if (c.type() == ComponentType.ASSIGNMENT && !effAssignments.isEmpty()) {
                range = assignmentRange(effAssignments, now);
                basis = "과제 %d건 동일 비중 가정 (미제출·마감 경과는 0점)".formatted(effAssignments.size());
            } else {
                range = new BigDecimal[]{BigDecimal.ZERO, HUNDRED};
                basis = "미평가 (0~100점)";
            }
            weightSum = weightSum.add(c.weight());
            lower = lower.add(c.weight().multiply(range[0]).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            upper = upper.add(c.weight().multiply(range[1]).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            breakdown.add(new ComponentBreakdown(c.name(), c.type(), c.weight(), scale(range[0]), scale(range[1]), basis));
        }

        boolean weightsValid = !components.isEmpty() && weightSum.subtract(HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) <= 0;
        if (!weightsValid) {
            warnings.add("평가 비율 합계가 %s%%입니다. 100%%가 되어야 점수 기반 진단이 가능합니다.".formatted(scale(weightSum).toPlainString()));
        }
        if (!rules.rulesConfirmed()) {
            warnings.add("평가 규칙이 사용자 확인 전 상태라 'F 확정'은 표시하지 않습니다.");
        }
        if (!wi.isEmpty()) {
            reasons.add(0, "가정 시뮬레이션 결과입니다: " + describeWhatIf(wi, assignments));
        }

        BigDecimal cutoff = rules.failCutoff();
        BigDecimal required = null;
        RiskStatus status;

        if (attendanceFail || assignmentFail) {
            status = rules.rulesConfirmed() ? RiskStatus.F_CONFIRMED : RiskStatus.HIGH;
            if (!rules.rulesConfirmed()) reasons.add("규칙이 확인되면 F 확정에 해당합니다.");
        } else if (!weightsValid || cutoff == null) {
            status = RiskStatus.CANNOT_CALCULATE;
            if (cutoff == null) warnings.add("F 기준 점수가 확인되지 않아 점수 기반 진단을 할 수 없습니다.");
        } else if (upper.compareTo(cutoff) < 0) {
            status = rules.rulesConfirmed() ? RiskStatus.F_CONFIRMED : RiskStatus.HIGH;
            reasons.add("남은 평가를 모두 만점 받아도 최고 %s점으로 F 기준 %s점에 못 미칩니다."
                    .formatted(scale(upper).toPlainString(), scale(cutoff).toPlainString()));
        } else if (lower.compareTo(cutoff) >= 0) {
            status = RiskStatus.SAFE;
            required = BigDecimal.ZERO;
            reasons.add("이미 확보한 점수 %s점이 F 기준 %s점 이상입니다.".formatted(scale(lower).toPlainString(), scale(cutoff).toPlainString()));
        } else {
            BigDecimal gap = upper.subtract(lower);
            required = cutoff.subtract(lower).multiply(HUNDRED).divide(gap, 1, RoundingMode.UP);
            status = required.compareTo(BigDecimal.valueOf(80)) >= 0 ? RiskStatus.HIGH
                    : required.compareTo(BigDecimal.valueOf(50)) >= 0 ? RiskStatus.MEDIUM
                    : RiskStatus.LOW;
            reasons.add("F를 피하려면 남은 평가에서 만점 대비 %s%% 이상을 받아야 합니다.".formatted(required.toPlainString()));
        }

        // 결석 여유가 거의 없으면 위험 단계를 끌어올린다
        if (status != RiskStatus.F_CONFIRMED && remainingAbsences != null && !attendanceFail) {
            if (remainingAbsences == 1) {
                reasons.add("한 번만 더 결석하면 결석 F 기준에 도달합니다.");
                status = raise(status, RiskStatus.HIGH);
            } else if (remainingAbsences == 2) {
                reasons.add("결석 2회가 더 쌓이면 결석 F 기준에 도달합니다.");
                status = raise(status, RiskStatus.MEDIUM);
            }
        }

        return new GradeDiagnosis(status, scale(lower), scale(upper), cutoff, required, effectiveAbsences,
                remainingAbsences, missed,
                breakdown, List.copyOf(reasons), List.copyOf(new LinkedHashSet<>(warnings)));
    }

    /** CANNOT_CALCULATE 는 정보 부족 상태이므로 결석 경고로 끌어올릴 때 HIGH/MEDIUM 으로 대체한다. */
    private static RiskStatus raise(RiskStatus current, RiskStatus atLeast) {
        if (current == RiskStatus.CANNOT_CALCULATE || current == RiskStatus.SAFE) return atLeast;
        return current.atLeast(atLeast) ? current : atLeast;
    }

    private static boolean isMissed(AssignmentItem a, OffsetDateTime now) {
        if (a.status() == AssignmentStatus.MISSED) return true;
        return a.status() == AssignmentStatus.PENDING && a.dueAt() != null && a.dueAt().isBefore(now);
    }

    private record AttendanceRange(BigDecimal lower, BigDecimal upper, String basis, String warning) {}

    private static AttendanceRange attendanceRange(Rules rules, int effectiveAbsences, int leftoverLates, int recorded) {
        if (rules.absencePenalty() == null && rules.latePenalty() == null) {
            return new AttendanceRange(BigDecimal.ZERO, HUNDRED, "감점 규칙 미확인 (0~100점)",
                    "출석 감점 규칙이 없어 출석 점수를 0~100점 범위로 계산했습니다.");
        }
        BigDecimal ap = Optional.ofNullable(rules.absencePenalty()).orElse(BigDecimal.ZERO);
        BigDecimal lp = Optional.ofNullable(rules.latePenalty()).orElse(BigDecimal.ZERO);
        BigDecimal current = HUNDRED
                .subtract(ap.multiply(BigDecimal.valueOf(effectiveAbsences)))
                .subtract(lp.multiply(BigDecimal.valueOf(leftoverLates)))
                .max(BigDecimal.ZERO);
        // 최저: 남은 수업을 모두 결석하는 경우 (전체 회차를 모르면 0점)
        BigDecimal floor = BigDecimal.ZERO;
        if (rules.totalSessions() != null) {
            int remainingSessions = Math.max(0, rules.totalSessions() - recorded);
            floor = current.subtract(ap.multiply(BigDecimal.valueOf(remainingSessions))).max(BigDecimal.ZERO);
        }
        return new AttendanceRange(floor, current,
                "현재 출석 기록 기준 최고 %s점 (결석 %s점·지각 %s점 감점)".formatted(
                        scale(current).toPlainString(), ap.stripTrailingZeros().toPlainString(), lp.stripTrailingZeros().toPlainString()),
                null);
    }

    private static BigDecimal[] assignmentRange(List<AssignmentItem> items, OffsetDateTime now) {
        BigDecimal lo = BigDecimal.ZERO;
        BigDecimal hi = BigDecimal.ZERO;
        for (AssignmentItem a : items) {
            if (isMissed(a, now)) {
                continue; // 0점
            }
            if (a.score() != null) {
                BigDecimal pct = a.score().multiply(HUNDRED).divide(a.maxScore(), 4, RoundingMode.HALF_UP);
                lo = lo.add(pct);
                hi = hi.add(pct);
            } else {
                hi = hi.add(HUNDRED); // 아직 채점 전/마감 전 → 0~100
            }
        }
        BigDecimal n = BigDecimal.valueOf(items.size());
        return new BigDecimal[]{lo.divide(n, 4, RoundingMode.HALF_UP), hi.divide(n, 4, RoundingMode.HALF_UP)};
    }

    private static String describeWhatIf(WhatIf wi, List<AssignmentItem> all) {
        List<String> parts = new ArrayList<>();
        if (wi.extraAbsences() > 0) parts.add("결석 %d회 추가".formatted(wi.extraAbsences()));
        if (wi.extraLates() > 0) parts.add("지각 %d회 추가".formatted(wi.extraLates()));
        if (wi.missAssignmentIds() != null) {
            all.stream().filter(a -> wi.missAssignmentIds().contains(a.id()))
                    .forEach(a -> parts.add("'%s' 미제출".formatted(a.title())));
        }
        return String.join(", ", parts);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
