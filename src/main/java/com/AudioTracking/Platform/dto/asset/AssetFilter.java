package com.AudioTracking.Platform.dto.asset;

import com.AudioTracking.Platform.entity.AssetType;

import java.util.UUID;

// Bundles GET /assets' optional query filters into one object instead of threading six
// individual parameters through controller -> service -> repository. Every field is optional;
// a null field means "don't filter on this."
public record AssetFilter(
        AssetType assetType,
        UUID projectId,
        UUID tagId,
        Integer minBpm,
        Integer maxBpm,
        String musicalKey
) {
}
