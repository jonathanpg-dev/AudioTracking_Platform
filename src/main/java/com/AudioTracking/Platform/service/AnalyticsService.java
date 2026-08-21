package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.entity.AnalyticsEventType;

import java.util.UUID;

// The write side of analytics: recording that something happened. Deliberately a single method
// (not one per event type) -- callers already know exactly which action they just completed
// successfully, so there's nothing a wider interface would add beyond more methods to maintain.
// See AnalyticsQueryService for the read/aggregation side, and docs/analytics.md for the full model.
public interface AnalyticsService {

    // userId is always the authenticated caller who performed the action -- callers must never
    // pass anything derived from request-body input, which is what makes event fabrication
    // impossible: there is no public endpoint that lets a client choose this value.
    // assetId/projectId are optional (pass null when not applicable to this eventType).
    //
    // Recording is best-effort: a failure here is logged, never thrown, so a problem in analytics
    // can never break the actual upload/share/delete/etc. it's attached to. See
    // AnalyticsServiceImpl and docs/analytics.md.
    void record(UUID userId, AnalyticsEventType eventType, UUID assetId, UUID projectId);
}
