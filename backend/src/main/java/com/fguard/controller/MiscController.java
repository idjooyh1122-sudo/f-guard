package com.fguard.controller;

import com.fguard.dto.SettingsDtos.*;
import com.fguard.service.DashboardService;
import com.fguard.service.DemoDataService;
import com.fguard.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MiscController {
    private final DashboardService dashboard;
    private final SettingsService settings;
    private final DemoDataService demo;

    public MiscController(DashboardService dashboard, SettingsService settings, DemoDataService demo) {
        this.dashboard = dashboard;
        this.settings = settings;
        this.demo = demo;
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP", "service", "f-guard"); }

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard() { return dashboard.get(); }

    @GetMapping("/settings")
    public SettingsResponse getSettings() { return settings.get(); }

    @PutMapping("/settings")
    public SettingsResponse updateSettings(@Valid @RequestBody SettingsRequest req) { return settings.update(req); }

    @GetMapping("/integrations")
    public IntegrationStatus integrations() { return settings.integrations(); }

    @PostMapping("/demo/seed")
    public Map<String, Integer> seed() { return Map.of("createdCourses", demo.seed()); }
}
