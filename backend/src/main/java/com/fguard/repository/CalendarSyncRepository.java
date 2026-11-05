package com.fguard.repository;

import com.fguard.entity.CalendarSync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarSyncRepository extends JpaRepository<CalendarSync, Long> {
    Optional<CalendarSync> findByAssignmentId(Long assignmentId);
    List<CalendarSync> findByAssignmentIdIn(List<Long> assignmentIds);
}
