package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.analytics.ActivityResponse;
import com.AudioTracking.Platform.dto.analytics.AnalyticsOverviewResponse;
import com.AudioTracking.Platform.dto.analytics.AssetAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.CollaborationAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.ProjectAnalyticsResponse;
import com.AudioTracking.Platform.entity.AnalyticsEventType;

import java.time.LocalDate;
import java.util.UUID;

// The read side of analytics: aggregating AnalyticsEvents (and, where the question isn't really
// about events at all -- current library size, current share counts -- the domain tables
// directly) into the small set of responses the API exposes. Every method takes the authenticated
// caller's id as its only identity input; there is no way to ask for anyone else's analytics
// through this interface. See AnalyticsService for the write side, and docs/analytics.md.
public interface AnalyticsQueryService {

    AnalyticsOverviewResponse getOverview(UUID userId);

    AssetAnalyticsResponse getAssetAnalytics(UUID userId, int topN);

    ProjectAnalyticsResponse getProjectAnalytics(UUID userId, int topN);

    CollaborationAnalyticsResponse getCollaborationAnalytics(UUID userId, int topN);

    // days/from/to: pass `from`+`to` for an explicit range, or `days` for "the last N days
    // including today", or neither for a 30-day default. eventType is optional (null = every type).
    ActivityResponse getActivity(UUID userId, Integer days, LocalDate from, LocalDate to, AnalyticsEventType eventType);
}
