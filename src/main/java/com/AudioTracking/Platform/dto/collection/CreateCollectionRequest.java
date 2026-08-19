package com.AudioTracking.Platform.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Reused for both create and update — a Collection's only client-editable field is its name.
// Membership (which assets it contains) is managed separately via addAsset/removeAsset.
public record CreateCollectionRequest(

        @NotBlank
        @Size(max = 150)
        String name
) {
}
