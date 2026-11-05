package com.fguard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "course")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;
    private String name;
    private String professor;
    private Integer credits;
    private Integer totalSessions;

    /** 결석 N회 이상이면 F (null = 확인되지 않음) */
    private Integer absenceFailThreshold;
    /** 지각 N회 = 결석 1회 (null = 환산 규칙 없음/미확인) */
    private Integer latesPerAbsence;
    /** 결석 1회당 출석 점수(100점 만점) 감점 */
    private BigDecimal absencePenalty;
    /** 지각 1회당 출석 점수(100점 만점) 감점 */
    private BigDecimal latePenalty;
    /** 총점이 이 점수 미만이면 F (null = 확인되지 않음) */
    private BigDecimal failCutoff;
    /** 과제 N개 이상 미제출 시 F */
    private Integer missingAssignmentFailCount;
    private String rulesNote;
    private boolean rulesConfirmed;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek, startTime")
    private List<ClassSchedule> schedules = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GradeComponent> components = new ArrayList<>();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getProfessor() { return professor; }
    public void setProfessor(String v) { professor = v; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer v) { credits = v; }
    public Integer getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Integer v) { totalSessions = v; }
    public Integer getAbsenceFailThreshold() { return absenceFailThreshold; }
    public void setAbsenceFailThreshold(Integer v) { absenceFailThreshold = v; }
    public Integer getLatesPerAbsence() { return latesPerAbsence; }
    public void setLatesPerAbsence(Integer v) { latesPerAbsence = v; }
    public BigDecimal getAbsencePenalty() { return absencePenalty; }
    public void setAbsencePenalty(BigDecimal v) { absencePenalty = v; }
    public BigDecimal getLatePenalty() { return latePenalty; }
    public void setLatePenalty(BigDecimal v) { latePenalty = v; }
    public BigDecimal getFailCutoff() { return failCutoff; }
    public void setFailCutoff(BigDecimal v) { failCutoff = v; }
    public Integer getMissingAssignmentFailCount() { return missingAssignmentFailCount; }
    public void setMissingAssignmentFailCount(Integer v) { missingAssignmentFailCount = v; }
    public String getRulesNote() { return rulesNote; }
    public void setRulesNote(String v) { rulesNote = v; }
    public boolean isRulesConfirmed() { return rulesConfirmed; }
    public void setRulesConfirmed(boolean v) { rulesConfirmed = v; }
    public List<ClassSchedule> getSchedules() { return schedules; }
    public List<GradeComponent> getComponents() { return components; }

    public void replaceSchedules(List<ClassSchedule> items) {
        schedules.clear();
        items.forEach(s -> { s.setCourse(this); schedules.add(s); });
    }

    public void replaceComponents(List<GradeComponent> items) {
        components.clear();
        items.forEach(c -> { c.setCourse(this); components.add(c); });
    }
}
