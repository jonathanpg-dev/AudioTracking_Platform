package com.AudioTracking.Platform.dto.asset;

import com.AudioTracking.Platform.dto.tag.TagResponse;
import com.AudioTracking.Platform.entity.AssetType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String title,
        String description,
        AssetType assetType,
        Integer bpm,
        String musicalKey,
        Integer durationSeconds,
        Long fileSizeBytes,
        String audioFormat,
        Instant createdAt,
        Instant updatedAt,
        List<TagResponse> tags,
        UUID projectId,
        String projectName
) {
}
