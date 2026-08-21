package com.AudioTracking.Platform.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// No status field here — a new project always starts at PLANNING, not something the client
// gets to pick. Any "status" the client sends would just be ignored (see UpdateProjectRequest
// for actually moving a project through its lifecycle).
//
// clientId is optional and unvalidated beyond being a well-formed UUID — ownership of the
// referenced Client is checked in the service layer, same convention as Asset.projectId.
public record CreateProjectRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 2000)
        String description,

        UUID clientId
) {
}
