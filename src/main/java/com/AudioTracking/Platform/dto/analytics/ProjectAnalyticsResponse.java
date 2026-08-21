package com.AudioTracking.Platform.dto.analytics;

import java.util.List;

public record ProjectAnalyticsResponse(
        long totalProjects,
        long totalProjectUpdates,
        List<ProjectActivityEntry> mostActiveProjects,
        List<ProjectAssetCountEntry> assetsPerProject
) {
}
