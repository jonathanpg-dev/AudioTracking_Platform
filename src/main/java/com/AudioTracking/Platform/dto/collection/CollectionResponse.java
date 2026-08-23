package com.AudioTracking.Platform.dto.collection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Deliberately lists asset ids, not full nested AssetResponse objects — Asset is a large object
// with its own nested tags/project, and embedding full assets here would make this payload
// unnecessarily heavy. Fetch individual assets via GET /api/v1/assets/{id} if full detail is needed.
public record CollectionResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        List<UUID> assetIds
) {
}
