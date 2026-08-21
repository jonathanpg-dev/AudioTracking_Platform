package com.AudioTracking.Platform.dto.project;

import com.AudioTracking.Platform.entity.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID clientId,
        String clientName
) {
}
