package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.asset.AssetFilter;
import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import org.springframework.data.domain.Pageable;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Collection;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.Tag;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.AssetMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AssetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ProjectRepository projectRepository;
    private final AssetMapper assetMapper;

    public AssetServiceImpl(AssetRepository assetRepository, UserRepository userRepository,
                             TagRepository tagRepository, ProjectRepository projectRepository,
                             AssetMapper assetMapper) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.projectRepository = projectRepository;
        this.assetMapper = assetMapper;
    }

    @Override
    public AssetResponse createAsset(UUID ownerId, CreateAssetRequest request) {
        Asset asset = assetMapper.toEntity(request);
        // getReferenceById avoids an extra SELECT: the caller is already an authenticated
        // user resolved from the JWT, so we only need their id to set the FK, not the full row.
        asset.setUser(userRepository.getReferenceById(ownerId));
        asset.setProject(resolveOwnedProjectOrNull(ownerId, request.projectId()));
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    @Override
    public List<AssetResponse> getAssets(UUID ownerId, AssetFilter filter, Pageable pageable) {
        return assetMapper.toResponseList(assetRepository.search(
                ownerId,
                filter.assetType(),
                filter.projectId(),
                filter.tagId(),
                filter.minBpm(),
                filter.maxBpm(),
                filter.musicalKey(),
                pageable).getContent());
    }

    @Override
    public AssetResponse getAsset(UUID ownerId, UUID assetId) {
        return assetMapper.toResponse(findOwnedOrThrow(ownerId, assetId));
    }

    @Override
    public AssetResponse updateAsset(UUID ownerId, UUID assetId, UpdateAssetRequest request) {
        Asset existing = findOwnedOrThrow(ownerId, assetId);
        assetMapper.updateEntity(request, existing);
        // Full-replace semantics like every other field on this DTO: omitting projectId (or
        // sending null) unassigns the asset from whatever project it was previously in.
        existing.setProject(resolveOwnedProjectOrNull(ownerId, request.projectId()));
        return assetMapper.toResponse(assetRepository.save(existing));
    }

    @Override
    @Transactional // collection-membership cleanup + the delete itself must succeed together
    public void deleteAsset(UUID ownerId, UUID assetId) {
        Asset existing = findOwnedOrThrow(ownerId, assetId);
        // Asset is the non-owning side of Collection<->Asset (Collection owns it), so unlike
        // asset_tags above, Hibernate won't clean up collection_assets automatically here.
        // Same object-graph approach as everywhere else that hit this: iterate a copy, since
        // removeAsset() mutates existing.getCollections() as we go.
        for (Collection collection : new ArrayList<>(existing.getCollections())) {
            collection.removeAsset(existing);
        }
        assetRepository.delete(existing);
    }

    @Override
    public AssetResponse addTag(UUID ownerId, UUID assetId, UUID tagId) {
        Asset asset = findOwnedOrThrow(ownerId, assetId);
        // Ownership check on the tag too: a user must never be able to attach someone else's
        // tag to their own asset. Same "wrong id and someone else's id look identical" 404.
        Tag tag = tagRepository.findByIdAndUserId(tagId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag with id '" + tagId + "' not found"));

        asset.addTag(tag); // keeps both sides of the bidirectional collection in sync; no-op if already attached
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    @Override
    public AssetResponse removeTag(UUID ownerId, UUID assetId, UUID tagId) {
        Asset asset = findOwnedOrThrow(ownerId, assetId);
        // No separate tag-ownership check needed here: asset.getTags() can only ever contain
        // tags this user owns in the first place (addTag enforces that), so filtering by id
        // alone is safe. Removing a tag that isn't attached is a no-op, not an error.
        asset.getTags().stream()
                .filter(tag -> tag.getId().equals(tagId))
                .findFirst()
                .ifPresent(asset::removeTag);
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    private Asset findOwnedOrThrow(UUID ownerId, UUID assetId) {
        return assetRepository.findByIdAndUserId(assetId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset with id '" + assetId + "' not found"));
    }

    // null projectId means "no project" — not an error. A non-null id that isn't owned by this
    // user is treated the same as a nonexistent one: a user must never be able to assign their
    // asset into someone else's project, and this must not reveal whether that project exists.
    private Project resolveOwnedProjectOrNull(UUID ownerId, UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findByIdAndUserId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project with id '" + projectId + "' not found"));
    }
}
