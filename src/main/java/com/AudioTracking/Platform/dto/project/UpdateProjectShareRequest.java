package com.AudioTracking.Platform.dto.project;

import com.AudioTracking.Platform.entity.ProjectPermission;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectShareRequest(
        @NotNull
        ProjectPermission permission
) {
}
