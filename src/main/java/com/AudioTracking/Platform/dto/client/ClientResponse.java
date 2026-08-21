package com.AudioTracking.Platform.dto.client;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        String email,
        String company,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
