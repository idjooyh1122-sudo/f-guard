package com.fguard.entity;

import com.fguard.domain.ComponentType;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "grade_component")
public class GradeComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    private ComponentType type;
    private String name;
    private BigDecimal weight;
    /** 100점 환산 점수. null = 아직 평가 전 */
    private BigDecimal score;

    public GradeComponent() {}

    public GradeComponent(ComponentType type, String name, BigDecimal weight, BigDecimal score) {
        this.type = type;
        this.name = name;
        this.weight = weight;
        this.score = score;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public void setCourse(Course v) { course = v; }
    public ComponentType getType() { return type; }
    public String getName() { return name; }
    public BigDecimal getWeight() { return weight; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { score = v; }
}
