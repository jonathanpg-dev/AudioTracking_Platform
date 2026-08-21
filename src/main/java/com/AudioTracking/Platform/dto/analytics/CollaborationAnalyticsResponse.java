package com.AudioTracking.Platform.dto.analytics;

import java.util.List;

// Scoped to Projects the caller OWNS -- "how much of my own work am I sharing", not a
// collaborator's view of someone else's workspace. See ProjectShareService: only an owner can
// ever see a Project's share list in the first place, and this reuses that exact same rule.
public record CollaborationAnalyticsResponse(
        long totalProjectsShared,
        long totalActiveCollaborators,
        long totalSharesCreated,
        List<SharedProjectEntry> mostSharedProjects
) {
}
