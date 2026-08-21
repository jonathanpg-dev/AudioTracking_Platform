package com.AudioTracking.Platform.dto.analytics;

import java.util.UUID;

// title is null when the Asset has since been deleted -- the ranking (and the historical count
// it's built from) still stands, there's just nothing left to display a name for. See
// AnalyticsEvent.assetId and docs/analytics.md.
public record AssetRankingEntry(
        UUID assetId,
        String title,
        long count
) {
}
