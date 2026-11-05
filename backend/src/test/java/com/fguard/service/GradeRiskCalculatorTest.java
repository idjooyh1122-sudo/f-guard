package com.fguard.service;

import com.fguard.domain.AssignmentStatus;
import com.fguard.domain.ComponentType;
import com.fguard.domain.GradeDiagnosis;
import com.fguard.domain.GradeDiagnosis.RiskStatus;
import com.fguard.domain.GradeInputs.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradeRiskCalculatorTest {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), KST); // 2026-11-05 12:00 KST
    static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    final GradeRiskCalculator calc = new GradeRiskCalculator(CLOCK);

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    static Rules rules(Integer absFail, Integer latesPerAbs, String absPen, String cutoff, boolean confirmed) {
        return new Rules(30, absFail, latesPerAbs, absPen == null ? null : bd(absPen), null,
                cutoff == null ? null : bd(cutoff), null, confirmed);
    }

    static List<Component> standardComponents(String midterm, String fin) {
        return List.of(
                new Component(ComponentType.ATTENDANCE, "출석", bd("10"), null),
                new Component(ComponentType.ASSIGNMENT, "과제", bd("20"), null),
                new Component(ComponentType.MIDTERM, "중간", bd("30"), midterm == null ? null : bd(midterm)),
                new Component(ComponentType.FINAL, "기말", bd("40"), fin == null ? null : bd(fin)));
    }

    @Test
    void 결석_기준_도달시_규칙_확인된_경우에만_F확정() {
        Attendance att = new Attendance(10, 0, 8, 0);
        GradeDiagnosis confirmed = calc.diagnose(rules(8, null, "10", "60", true), standardComponents(null, null), List.of(), att, null);
        assertThat(confirmed.status()).isEqualTo(RiskStatus.F_CONFIRMED);
        assertThat(confirmed.effectiveAbsences()).isEqualTo(8);

        GradeDiagnosis unconfirmed = calc.diagnose(rules(8, null, "10", "60", false), standardComponents(null, null), List.of(), att, null);
        assertThat(unconfirmed.status()).isEqualTo(RiskStatus.HIGH);
        assertThat(unconfirmed.reasons()).anyMatch(r -> r.contains("규칙이 확인되면"));
    }

    @Test
    void 지각_3회는_결석_1회로_환산() {
        Attendance att = new Attendance(10, 7, 2, 0); // 지각 7 → 결석 2 + 지각 1
        GradeDiagnosis d = calc.diagnose(rules(8, 3, "10", "60", true), standardComponents(null, null), List.of(), att, null);
        assertThat(d.effectiveAbsences()).isEqualTo(4);
        assertThat(d.remainingAbsencesBeforeF()).isEqualTo(4);
        // 출석 최고점: 100 - 4*10 = 60 → 총점 기여 6.00
        assertThat(d.components().getFirst().upperScore()).isEqualByComparingTo("60");
    }

    @Test
    void 남은평가_만점이어도_기준미달이면_F확정() {
        // 출석: 결석 7회(감점10) → 최고 30점 → 3.0 / 과제: 2건 모두 미제출 → 0 / 중간 0점 / 기말 미정(최대 40)
        Attendance att = new Attendance(5, 0, 7, 0);
        List<AssignmentItem> as = List.of(
                new AssignmentItem(1L, "과제1", NOW.minusDays(10), bd("100"), null, AssignmentStatus.MISSED),
                new AssignmentItem(2L, "과제2", NOW.minusDays(1), bd("100"), null, AssignmentStatus.PENDING)); // 마감 경과
        GradeDiagnosis d = calc.diagnose(rules(8, null, "10", "60", true), standardComponents("0", null), as, att, null);
        assertThat(d.upperBound()).isEqualByComparingTo("43.00");
        assertThat(d.status()).isEqualTo(RiskStatus.F_CONFIRMED);
        assertThat(d.missedAssignments()).isEqualTo(2);
    }

    @Test
    void 확보점수가_기준이상이면_안전() {
        List<Component> comps = List.of(
                new Component(ComponentType.MIDTERM, "중간", bd("50"), bd("100")),
                new Component(ComponentType.FINAL, "기말", bd("50"), bd("30")));
        GradeDiagnosis d = calc.diagnose(rules(null, null, null, "60", true), comps, List.of(), new Attendance(0, 0, 0, 0), null);
        assertThat(d.lowerBound()).isEqualByComparingTo("65.00");
        assertThat(d.status()).isEqualTo(RiskStatus.SAFE);
    }

    @Test
    void 필요비율_역산() {
        // 중간 50%에서 40점 → 20 확보, 기말 50% 미정 → 최고 70. 60 넘으려면 남은 50 중 40 필요 = 80%
        List<Component> comps = List.of(
                new Component(ComponentType.MIDTERM, "중간", bd("50"), bd("40")),
                new Component(ComponentType.FINAL, "기말", bd("50"), null));
        GradeDiagnosis d = calc.diagnose(rules(null, null, null, "60", true), comps, List.of(), new Attendance(0, 0, 0, 0), null);
        assertThat(d.requiredPercentOfRemaining()).isEqualByComparingTo("80.0");
        assertThat(d.status()).isEqualTo(RiskStatus.HIGH);
    }

    @Test
    void F기준_또는_비율이_없으면_계산불가() {
        GradeDiagnosis noCutoff = calc.diagnose(rules(null, null, null, null, true), standardComponents(null, null), List.of(), new Attendance(0, 0, 0, 0), null);
        assertThat(noCutoff.status()).isEqualTo(RiskStatus.CANNOT_CALCULATE);

        List<Component> partial = List.of(new Component(ComponentType.MIDTERM, "중간", bd("40"), null));
        GradeDiagnosis badWeights = calc.diagnose(rules(null, null, null, "60", true), partial, List.of(), new Attendance(0, 0, 0, 0), null);
        assertThat(badWeights.status()).isEqualTo(RiskStatus.CANNOT_CALCULATE);
        assertThat(badWeights.warnings()).anyMatch(w -> w.contains("합계가 40.00%"));
    }

    @Test
    void 과제_미제출_가정시뮬레이션() {
        // 기획서 예시: "이번 과제를 놓치면 F 확정"
        Rules r = new Rules(30, 8, null, bd("10"), null, bd("60"), 2, true);
        List<AssignmentItem> as = List.of(
                new AssignmentItem(1L, "과제1", NOW.minusDays(10), bd("100"), null, AssignmentStatus.MISSED),
                new AssignmentItem(2L, "과제2", NOW.plusDays(3), bd("100"), null, AssignmentStatus.PENDING));
        Attendance att = new Attendance(10, 0, 0, 0);
        GradeDiagnosis before = calc.diagnose(r, standardComponents(null, null), as, att, null);
        assertThat(before.status()).isNotEqualTo(RiskStatus.F_CONFIRMED);

        GradeDiagnosis after = calc.diagnose(r, standardComponents(null, null), as, att, new WhatIf(0, 0, List.of(2L)));
        assertThat(after.status()).isEqualTo(RiskStatus.F_CONFIRMED);
        assertThat(after.reasons().getFirst()).contains("가정 시뮬레이션").contains("과제2");
    }

    @Test
    void 결석_여유가_1회면_위험단계를_HIGH로() {
        List<Component> comps = List.of(
                new Component(ComponentType.MIDTERM, "중간", bd("50"), bd("100")),
                new Component(ComponentType.FINAL, "기말", bd("50"), bd("100")));
        GradeDiagnosis d = calc.diagnose(rules(4, null, null, "60", true), comps, List.of(), new Attendance(5, 0, 3, 0), null);
        assertThat(d.remainingAbsencesBeforeF()).isEqualTo(1);
        assertThat(d.status()).isEqualTo(RiskStatus.HIGH);
    }
}
