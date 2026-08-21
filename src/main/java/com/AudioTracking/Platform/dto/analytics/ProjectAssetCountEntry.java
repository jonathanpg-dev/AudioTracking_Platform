package com.AudioTracking.Platform.dto.analytics;

import java.util.UUID;

public record ProjectAssetCountEntry(
        UUID projectId,
        String projectName,
        long assetCount
) {
}
