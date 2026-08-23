package com.AudioTracking.Platform.dto.project;

import com.AudioTracking.Platform.entity.ProjectRole;
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
        String clientName,
        // The caller's own relationship to this Project -- OWNER, VIEW, or EDIT. Backend-computed
        // so the frontend never has to (and never should) work this out itself. See ProjectRole.
        ProjectRole myRole
) {
}
