package com.AudioTracking.Platform.dto.analytics;

import java.util.UUID;

// projectName is null when the Project has since been deleted -- same reasoning as
// AssetRankingEntry.title.
public record ProjectActivityEntry(
        UUID projectId,
        String projectName,
        long eventCount
) {
}
