package com.fguard.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "google_credential")
public class GoogleCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private OffsetDateTime expiresAt;
    private String scope;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String v) { accessToken = v; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String v) { refreshToken = v; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime v) { expiresAt = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { scope = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }

    @Override
    public String toString() {
        // 토큰이 로그에 남지 않도록 의도적으로 제외
        return "GoogleCredential{userId=" + userId + ", expiresAt=" + expiresAt + "}";
    }
}
