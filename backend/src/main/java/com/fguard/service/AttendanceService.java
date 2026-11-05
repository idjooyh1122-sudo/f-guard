package com.fguard.service;

import com.fguard.config.ApiException;
import com.fguard.dto.RecordDtos.*;
import com.fguard.entity.AttendanceRecord;
import com.fguard.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AttendanceService {
    private final AttendanceRecordRepository records;
    private final CourseService courseService;

    public AttendanceService(AttendanceRecordRepository records, CourseService courseService) {
        this.records = records;
        this.courseService = courseService;
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> list(Long courseId) {
        courseService.require(courseId);
        return records.findByCourseIdOrderByClassDateDesc(courseId).stream().map(AttendanceService::toResponse).toList();
    }

    /** 같은 날짜 기록이 있으면 갱신 (과목·날짜당 1건) */
    public AttendanceResponse upsert(Long courseId, AttendanceRequest req) {
        courseService.require(courseId);
        AttendanceRecord r = records.findByCourseIdAndClassDate(courseId, req.classDate()).orElseGet(AttendanceRecord::new);
        r.setCourseId(courseId);
        r.setClassDate(req.classDate());
        r.setStatus(req.status());
        r.setNote(req.note());
        return toResponse(records.save(r));
    }

    public void delete(Long courseId, Long recordId) {
        courseService.require(courseId);
        AttendanceRecord r = records.findById(recordId)
                .filter(x -> x.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.notFound("출석 기록"));
        records.delete(r);
    }

    @Transactional(readOnly = true)
    public AttendanceSummary summary(Long courseId) {
        List<AttendanceRecord> list = records.findByCourseIdOrderByClassDateDesc(courseId);
        int[] c = new int[4];
        for (AttendanceRecord r : list) c[r.getStatus().ordinal()]++;
        return new AttendanceSummary(courseId, c[0], c[1], c[2], c[3]);
    }

    static AttendanceResponse toResponse(AttendanceRecord r) {
        return new AttendanceResponse(r.getId(), r.getCourseId(), r.getClassDate(), r.getStatus(), r.getNote());
    }
}
