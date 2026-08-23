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
        String projectName,
        // Deliberately a boolean, not the raw storageKey — the internal object-key format is an
        // implementation detail, not something clients should see or depend on.
        boolean hasAudioFile,
        // Feedback from the project's client — writable only via PUT .../client-notes by the
        // Project's linked client, but visible to anyone with view+ access, same as every other
        // field here. Null means no client feedback yet. See AssetService#updateClientNotes.
        String clientNotes
) {
}
