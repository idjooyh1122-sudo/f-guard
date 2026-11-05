package com.fguard.repository;

import com.fguard.entity.GoogleCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleCredentialRepository extends JpaRepository<GoogleCredential, Long> {
    Optional<GoogleCredential> findByUserId(Long userId);
}
