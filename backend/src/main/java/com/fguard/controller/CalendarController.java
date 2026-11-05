package com.fguard.controller;

import com.fguard.service.CalendarService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    public record SyncRequest(List<Long> assignmentIds) {}

    @GetMapping("/google/status")
    public CalendarService.GoogleStatus status() { return calendar.status(); }

    @GetMapping("/google/auth-url")
    public Map<String, String> authUrl() { return Map.of("url", calendar.authorizationUrl()); }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(calendar.handleCallback(code, state, error))).build();
    }

    @DeleteMapping("/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() { calendar.disconnect(); }

    @PostMapping("/sync")
    public List<CalendarService.SyncItemResult> sync(@RequestBody SyncRequest req) {
        return calendar.sync(req.assignmentIds() == null ? List.of() : req.assignmentIds());
    }

    @GetMapping(value = "/export.ics")
    public ResponseEntity<byte[]> ics() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"f-guard-deadlines.ics\"")
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .body(calendar.exportIcs().getBytes(StandardCharsets.UTF_8));
    }
}
