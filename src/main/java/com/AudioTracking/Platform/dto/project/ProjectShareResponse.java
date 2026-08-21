package com.AudioTracking.Platform.dto.project;

import com.AudioTracking.Platform.entity.ProjectPermission;

import java.time.Instant;
import java.util.UUID;

// Deliberately lean: just enough for a collaborator-management UI. Never exposes anything from
// User beyond id/username/email -- no password hash, no googleId, no other account internals.
public record ProjectShareResponse(
        UUID id,
        UUID userId,
        String username,
        String email,
        ProjectPermission permission,
        Instant createdAt
) {
}
