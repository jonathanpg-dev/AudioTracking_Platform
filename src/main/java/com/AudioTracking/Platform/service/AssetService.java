package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.asset.AssetFilter;
import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AssetService {

    AssetResponse createAsset(UUID ownerId, CreateAssetRequest request);

    // pageable may be Pageable.unpaged() for "give me everything" (matches the pre-STEP-7
    // behavior); filter fields left null mean "don't filter on this".
    List<AssetResponse> getAssets(UUID ownerId, AssetFilter filter, Pageable pageable);

    AssetResponse getAsset(UUID ownerId, UUID assetId);

    AssetResponse updateAsset(UUID ownerId, UUID assetId, UpdateAssetRequest request);

    void deleteAsset(UUID ownerId, UUID assetId);

    AssetResponse addTag(UUID ownerId, UUID assetId, UUID tagId);

    AssetResponse removeTag(UUID ownerId, UUID assetId, UUID tagId);
}
