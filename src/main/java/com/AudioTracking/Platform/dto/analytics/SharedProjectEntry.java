package com.AudioTracking.Platform.dto.analytics;

import java.util.UUID;

// Built from live ProjectShare rows (not the event log) -- a deleted Project cascades its shares
// away too (see Project.shares), so there's never a dangling id to worry about here.
public record SharedProjectEntry(
        UUID projectId,
        String projectName,
        long collaboratorCount
) {
}
