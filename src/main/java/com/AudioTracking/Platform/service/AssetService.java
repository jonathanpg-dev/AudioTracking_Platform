package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.asset.AssetFilter;
import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.FileAccessResponse;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

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

    // Uploads a new file, or safely replaces an existing one (see AssetServiceImpl for the
    // upload-then-cleanup ordering that keeps this safe against partial failure).
    AssetResponse uploadFile(UUID ownerId, UUID assetId, MultipartFile file);

    // Generates a fresh short-lived presigned URL — never reads a stored one, because none exists.
    FileAccessResponse getFileAccessUrl(UUID ownerId, UUID assetId);

    AssetResponse deleteFile(UUID ownerId, UUID assetId);
}
