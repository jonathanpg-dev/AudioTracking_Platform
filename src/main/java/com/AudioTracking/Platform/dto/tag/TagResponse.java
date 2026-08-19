package com.AudioTracking.Platform.dto.tag;

import java.time.Instant;
import java.util.UUID;

public record TagResponse(
        UUID id,
        String name,
        Instant createdAt
) {
}
