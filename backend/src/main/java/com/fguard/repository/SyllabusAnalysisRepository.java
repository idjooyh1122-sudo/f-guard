package com.fguard.repository;

import com.fguard.entity.SyllabusAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyllabusAnalysisRepository extends JpaRepository<SyllabusAnalysis, Long> {
    List<SyllabusAnalysis> findByUserIdOrderByIdDesc(Long userId);
    Optional<SyllabusAnalysis> findByIdAndUserId(Long id, Long userId);
}
