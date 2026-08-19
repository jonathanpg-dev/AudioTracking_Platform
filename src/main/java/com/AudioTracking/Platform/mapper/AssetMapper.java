package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import com.AudioTracking.Platform.dto.tag.TagResponse;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Project;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AssetMapper {

    private final TagMapper tagMapper;

    public AssetMapper(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    // Note: project is deliberately not set here — resolving a projectId into a Project
    // requires a repository lookup plus an ownership check, which belongs in the service
    // layer (same reason asset.setUser(...) also happens there, not in this mapper).
    public Asset toEntity(CreateAssetRequest request) {
        Asset asset = new Asset();
        asset.setTitle(request.title());
        asset.setDescription(request.description());
        asset.setAssetType(request.assetType());
        asset.setBpm(request.bpm());
        asset.setMusicalKey(request.musicalKey());
        asset.setDurationSeconds(request.durationSeconds());
        asset.setFileSizeBytes(request.fileSizeBytes());
        asset.setAudioFormat(request.audioFormat());
        return asset;
    }

    public void updateEntity(UpdateAssetRequest request, Asset existing) {
        existing.setTitle(request.title());
        existing.setDescription(request.description());
        existing.setAssetType(request.assetType());
        existing.setBpm(request.bpm());
        existing.setMusicalKey(request.musicalKey());
        existing.setDurationSeconds(request.durationSeconds());
        existing.setFileSizeBytes(request.fileSizeBytes());
        existing.setAudioFormat(request.audioFormat());
    }

    public AssetResponse toResponse(Asset asset) {
        List<TagResponse> tags = asset.getTags().stream()
                .map(tagMapper::toResponse)
                .sorted(Comparator.comparing(TagResponse::name))
                .toList();

        Project project = asset.getProject();

        return new AssetResponse(
                asset.getId(),
                asset.getTitle(),
                asset.getDescription(),
                asset.getAssetType(),
                asset.getBpm(),
                asset.getMusicalKey(),
                asset.getDurationSeconds(),
                asset.getFileSizeBytes(),
                asset.getAudioFormat(),
                asset.getCreatedAt(),
                asset.getUpdatedAt(),
                tags,
                project == null ? null : project.getId(),
                project == null ? null : project.getName());
    }

    public List<AssetResponse> toResponseList(List<Asset> assets) {
        return assets.stream().map(this::toResponse).toList();
    }
}
