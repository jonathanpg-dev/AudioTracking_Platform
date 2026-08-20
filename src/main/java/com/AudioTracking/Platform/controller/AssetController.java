package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.asset.AssetFilter;
import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.FileAccessResponse;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import com.AudioTracking.Platform.entity.AssetType;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    public ResponseEntity<AssetResponse> createAsset(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                       @Valid @RequestBody CreateAssetRequest request) {
        AssetResponse response = assetService.createAsset(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AssetResponse>> getAssets(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                           @RequestParam(required = false) AssetType assetType,
                                                           @RequestParam(required = false) UUID projectId,
                                                           @RequestParam(required = false) UUID tagId,
                                                           @RequestParam(required = false) Integer minBpm,
                                                           @RequestParam(required = false) Integer maxBpm,
                                                           @RequestParam(required = false) String musicalKey,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        AssetFilter filter = new AssetFilter(assetType, projectId, tagId, minBpm, maxBpm, musicalKey);
        Sort sort = Sort.by("createdAt").descending();
        Pageable pageable = (page != null && size != null) ? PageRequest.of(page, size, sort) : Pageable.unpaged(sort);
        return ResponseEntity.ok(assetService.getAssets(currentUser.getId(), filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getAsset(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                   @PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getAsset(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssetResponse> updateAsset(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody UpdateAssetRequest request) {
        return ResponseEntity.ok(assetService.updateAsset(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(@AuthenticationPrincipal CustomUserDetails currentUser,
                                             @PathVariable UUID id) {
        assetService.deleteAsset(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tags/{tagId}")
    public ResponseEntity<AssetResponse> addTag(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                 @PathVariable UUID id,
                                                 @PathVariable UUID tagId) {
        return ResponseEntity.ok(assetService.addTag(currentUser.getId(), id, tagId));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ResponseEntity<AssetResponse> removeTag(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                    @PathVariable UUID id,
                                                    @PathVariable UUID tagId) {
        return ResponseEntity.ok(assetService.removeTag(currentUser.getId(), id, tagId));
    }

    // Uploads a new file, or replaces the existing one — same endpoint handles both, since
    // "replace" is just "upload" when a file is already present (see AssetServiceImpl).
    @PostMapping("/{id}/file")
    public ResponseEntity<AssetResponse> uploadFile(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                      @PathVariable UUID id,
                                                      @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(assetService.uploadFile(currentUser.getId(), id, file));
    }

    // Returns a short-lived signed URL, not the file itself — the file stays private in R2 and
    // is never proxied through this server or exposed at a permanent public address.
    @GetMapping("/{id}/file")
    public ResponseEntity<FileAccessResponse> getFileAccessUrl(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                 @PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getFileAccessUrl(currentUser.getId(), id));
    }

    @DeleteMapping("/{id}/file")
    public ResponseEntity<AssetResponse> deleteFile(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                     @PathVariable UUID id) {
        return ResponseEntity.ok(assetService.deleteFile(currentUser.getId(), id));
    }
}
