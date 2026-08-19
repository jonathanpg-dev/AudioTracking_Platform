package com.AudioTracking.Platform.dto.asset;

import com.AudioTracking.Platform.entity.AssetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// projectId is optional and unvalidated beyond being a well-formed UUID — ownership of the
// referenced project is checked in the service layer, since that requires a repository lookup.
// Omitting it (or sending null) unassigns the asset from whatever project it was in, matching
// this DTO's full-replace PUT semantics everywhere else.
public record UpdateAssetRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 2000)
        String description,

        @NotNull
        AssetType assetType,

        @Min(20)
        @Max(300)
        Integer bpm,

        @Size(max = 30)
        String musicalKey,

        @PositiveOrZero
        Integer durationSeconds,

        @PositiveOrZero
        Long fileSizeBytes,

        @Size(max = 10)
        String audioFormat,

        UUID projectId
) {
}
