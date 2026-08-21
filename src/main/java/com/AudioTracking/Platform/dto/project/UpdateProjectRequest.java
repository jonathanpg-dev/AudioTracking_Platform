package com.AudioTracking.Platform.dto.project;

import com.AudioTracking.Platform.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Full-replace semantics for clientId like every other optional-association field in this app
// (see Asset.projectId): omitting it (or sending null) removes the Client association.
public record UpdateProjectRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull
        ProjectStatus status,

        UUID clientId
) {
}
