package com.fguard.repository;

import com.fguard.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourseIdOrderByDueAtAsc(Long courseId);
    List<Assignment> findByCourseIdInOrderByDueAtAsc(List<Long> courseIds);
}
