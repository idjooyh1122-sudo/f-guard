package com.fguard.dto;

import jakarta.validation.constraints.*;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record SettingsRequest(
            @NotBlank @Size(max = 50) String displayName,
            @Size(max = 100) String homeLabel,
            @DecimalMin("33") @DecimalMax("39") Double homeLat,
            @DecimalMin("124") @DecimalMax("132") Double homeLng,
            @Size(max = 100) String campusLabel,
            @DecimalMin("33") @DecimalMax("39") Double campusLat,
            @DecimalMin("124") @DecimalMax("132") Double campusLng,
            @Min(0) @Max(300) int prepMinutes,
            @Min(0) @Max(300) int baseTravelMinutes,
            @Min(0) @Max(120) int bufferMinutes) {}

    public record SettingsResponse(
            String displayName, String homeLabel, Double homeLat, Double homeLng,
            String campusLabel, Double campusLat, Double campusLng,
            int prepMinutes, int baseTravelMinutes, int bufferMinutes) {}

    public record IntegrationStatus(
            Item openai, Item weather, Item travel, Item google) {}

    public record Item(boolean configured, boolean connected, String mode, String detail) {}
}
