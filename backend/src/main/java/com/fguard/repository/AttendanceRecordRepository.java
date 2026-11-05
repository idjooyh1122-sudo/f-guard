package com.fguard.repository;

import com.fguard.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByCourseIdOrderByClassDateDesc(Long courseId);
    List<AttendanceRecord> findByCourseIdInAndClassDateAfter(List<Long> courseIds, LocalDate after);
    Optional<AttendanceRecord> findByCourseIdAndClassDate(Long courseId, LocalDate date);
}
