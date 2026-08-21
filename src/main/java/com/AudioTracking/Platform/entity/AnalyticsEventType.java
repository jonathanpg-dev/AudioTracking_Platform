package com.AudioTracking.Platform.entity;

// Deliberately a small, fixed set -- only events that feed a real analytics metric. See
// docs/analytics.md before adding a new one.
public enum AnalyticsEventType {
    ASSET_UPLOADED,
    ASSET_PLAYED,
    ASSET_DOWNLOADED,
    ASSET_DELETED,

    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_SHARED,

    COLLECTION_CREATED,

    CLIENT_CREATED
}
