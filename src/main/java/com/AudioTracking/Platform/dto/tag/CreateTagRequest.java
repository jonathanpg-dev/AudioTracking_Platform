package com.AudioTracking.Platform.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Reused for both create and update — a Tag's only client-editable field is its name, so a
// separate UpdateTagRequest would just be a duplicate of this class.
public record CreateTagRequest(

        @NotBlank
        @Size(max = 50)
        String name
) {
}
