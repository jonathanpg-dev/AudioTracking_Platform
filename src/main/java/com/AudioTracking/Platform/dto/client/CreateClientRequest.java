package com.AudioTracking.Platform.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No owner/user field — the owner is always the authenticated caller, never client-supplied.
public record CreateClientRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Email
        @Size(max = 254)
        String email,

        @Size(max = 150)
        String company,

        @Size(max = 2000)
        String notes
) {
}
