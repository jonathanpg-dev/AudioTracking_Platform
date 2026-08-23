package com.AudioTracking.Platform.dto.asset;

import com.AudioTracking.Platform.entity.AssetType;

import java.util.List;
import java.util.UUID;

// Bundles GET /assets' optional query filters into one object instead of threading nine
// individual parameters through controller -> service -> repository. Every field is optional;
// a null (or, for tagIds, empty) field means "don't filter on this."
//
// tagIds matches AND, not OR -- an asset must carry every listed tag, not just one of them. See
// AssetRepository#search for how that's enforced.
public record AssetFilter(
        AssetType assetType,
        UUID projectId,
        List<UUID> tagIds,
        Integer minBpm,
        Integer maxBpm,
        String musicalKey,
        String audioFormat,
        Integer minDurationSeconds,
        Integer maxDurationSeconds
) {
}
