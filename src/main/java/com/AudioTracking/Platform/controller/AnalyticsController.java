package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.analytics.ActivityResponse;
import com.AudioTracking.Platform.dto.analytics.AnalyticsOverviewResponse;
import com.AudioTracking.Platform.dto.analytics.AssetAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.CollaborationAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.ProjectAnalyticsResponse;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.AnalyticsQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// Every endpoint here is scoped to the authenticated caller ONLY -- none of them accept a userId
// (or any other identity) from the request, so there is no way to retrieve anyone else's
// analytics through this API. See docs/analytics.md.
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    // Caps how many ranking rows a single request can ask for -- generous enough for any
    // realistic UI, small enough that the query stays cheap regardless of input.
    private static final int MAX_RANKING_SIZE = 50;
    private static final int DEFAULT_RANKING_SIZE = 5;

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(analyticsQueryService.getOverview(currentUser.getId()));
    }

    @GetMapping("/assets")
    public ResponseEntity<AssetAnalyticsResponse> getAssetAnalytics(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                       @RequestParam(required = false) Integer topN) {
        return ResponseEntity.ok(analyticsQueryService.getAssetAnalytics(currentUser.getId(), resolveTopN(topN)));
    }

    @GetMapping("/projects")
    public ResponseEntity<ProjectAnalyticsResponse> getProjectAnalytics(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                          @RequestParam(required = false) Integer topN) {
        return ResponseEntity.ok(analyticsQueryService.getProjectAnalytics(currentUser.getId(), resolveTopN(topN)));
    }

    @GetMapping("/collaboration")
    public ResponseEntity<CollaborationAnalyticsResponse> getCollaborationAnalytics(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                                       @RequestParam(required = false) Integer topN) {
        return ResponseEntity.ok(analyticsQueryService.getCollaborationAnalytics(currentUser.getId(), resolveTopN(topN)));
    }

    // days=7 / days=30 cover the spec's explicit "last 7 days" / "last 30 days" cases; an
    // explicit from/to range is also supported for anything else. eventType narrows to one
    // AnalyticsEventType; omitted means "every type".
    @GetMapping("/activity")
    public ResponseEntity<ActivityResponse> getActivity(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                          @RequestParam(required = false) Integer days,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                          @RequestParam(required = false) AnalyticsEventType eventType) {
        return ResponseEntity.ok(analyticsQueryService.getActivity(currentUser.getId(), days, from, to, eventType));
    }

    private int resolveTopN(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_RANKING_SIZE;
        }
        return Math.min(requested, MAX_RANKING_SIZE);
    }
}
