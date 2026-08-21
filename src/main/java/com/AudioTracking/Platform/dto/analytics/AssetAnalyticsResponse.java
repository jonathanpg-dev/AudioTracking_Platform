package com.AudioTracking.Platform.dto.analytics;

import java.util.List;

// Every field here is scoped to actions the CALLER personally performed (uploads/plays/
// downloads/deletions they triggered) -- see docs/analytics.md for why this reading was chosen
// over "how popular are my uploads with others", and why it's the one that stays internally
// consistent and survives Asset deletion without special-casing.
public record AssetAnalyticsResponse(
        long totalUploads,
        long totalPlays,
        long totalDownloads,
        long totalDeletions,
        List<AssetRankingEntry> topPlayedAssets,
        List<AssetRankingEntry> topDownloadedAssets
) {
}
