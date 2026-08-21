package com.AudioTracking.Platform.dto.project;

import com.AudioTracking.Platform.entity.ProjectPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// The collaborator is identified by email (an existing, unique User field) rather than a raw user
// id -- the owner shares like they'd share a doc, by the email they know the collaborator by, not
// an opaque UUID. The target User must already be registered: this never creates one.
public record CreateProjectShareRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String userEmail,

        @NotNull
        ProjectPermission permission
) {
}
