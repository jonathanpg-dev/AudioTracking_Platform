package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.asset.AssetFilter;
import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.FileAccessResponse;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import org.springframework.data.domain.Pageable;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Collection;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.Tag;
import com.AudioTracking.Platform.exception.InvalidFileException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.exception.StorageException;
import com.AudioTracking.Platform.mapper.AssetMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AssetService;
import com.AudioTracking.Platform.storage.AudioFileValidator;
import com.AudioTracking.Platform.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AssetServiceImpl implements AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetServiceImpl.class);

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ProjectRepository projectRepository;
    private final StorageService storageService;
    private final AssetMapper assetMapper;
    private final long presignedUrlExpirationMinutes;

    public AssetServiceImpl(AssetRepository assetRepository, UserRepository userRepository,
                             TagRepository tagRepository, ProjectRepository projectRepository,
                             StorageService storageService, AssetMapper assetMapper,
                             @Value("${storage.presigned-url-expiration-minutes}") long presignedUrlExpirationMinutes) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.projectRepository = projectRepository;
        this.storageService = storageService;
        this.assetMapper = assetMapper;
        this.presignedUrlExpirationMinutes = presignedUrlExpirationMinutes;
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

        // Best-effort, deliberately not propagated: the Asset (and its DB reference to this
        // object) is being permanently removed either way, so failing the whole deletion over a
        // storage-provider hiccup would be worse than leaving one orphaned R2 object behind.
        // Contrast with deleteFile() below, where the file removal IS the user's explicit intent
        // and a failure there must be surfaced, not swallowed.
        if (existing.getStorageKey() != null) {
            try {
                storageService.delete(existing.getStorageKey());
            } catch (StorageException e) {
                log.warn("Failed to delete storage object for asset {} being deleted: {}", assetId, e.getMessage());
            }
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

    @Override
    public AssetResponse uploadFile(UUID ownerId, UUID assetId, MultipartFile file) {
        Asset asset = findOwnedOrThrow(ownerId, assetId);
        AudioFileValidator.ValidatedAudioFile validated = AudioFileValidator.validate(file);

        String previousKey = asset.getStorageKey(); // null if this is a first-time upload
        String newKey = buildStorageKey(ownerId, assetId, validated.extension());

        // Order matters here (validate -> upload -> persist -> clean up old): the new object
        // must be safely in R2 and the database updated to point at it BEFORE the old object is
        // touched. That way a failure at any point leaves the asset pointing at a real object —
        // either the old one (nothing succeeded yet) or the new one (everything succeeded) —
        // never at nothing.
        try (InputStream in = file.getInputStream()) {
            storageService.upload(newKey, in, file.getSize(), validated.contentType());
        } catch (IOException e) {
            throw new InvalidFileException("Could not read uploaded file");
        }

        asset.setStorageKey(newKey);
        asset.setFileSizeBytes(file.getSize());
        asset.setAudioFormat(validated.extension());
        Asset saved = assetRepository.save(asset);

        if (previousKey != null) {
            // Best-effort: the asset already correctly points at the new file regardless of
            // whether this succeeds, so a failure here just means one harmless orphaned object,
            // not an inconsistent asset.
            try {
                storageService.delete(previousKey);
            } catch (StorageException e) {
                log.warn("Failed to delete replaced storage object for asset {}: {}", assetId, e.getMessage());
            }
        }

        return assetMapper.toResponse(saved);
    }

    @Override
    public FileAccessResponse getFileAccessUrl(UUID ownerId, UUID assetId) {
        Asset asset = findOwnedOrThrow(ownerId, assetId);
        if (asset.getStorageKey() == null) {
            throw new ResourceNotFoundException("Asset with id '" + assetId + "' has no associated audio file");
        }

        Duration expiration = Duration.ofMinutes(presignedUrlExpirationMinutes);
        URI url = storageService.generatePresignedDownloadUrl(asset.getStorageKey(), expiration);
        return new FileAccessResponse(url.toString(), Instant.now().plus(expiration));
    }

    @Override
    public AssetResponse deleteFile(UUID ownerId, UUID assetId) {
        Asset asset = findOwnedOrThrow(ownerId, assetId);
        if (asset.getStorageKey() == null) {
            throw new ResourceNotFoundException("Asset with id '" + assetId + "' has no associated audio file");
        }

        // Deliberately NOT best-effort, unlike deleteAsset/uploadFile's old-object cleanup above:
        // this endpoint's entire purpose is "remove the file," so if that genuinely fails, the
        // database reference must stay intact rather than silently claiming the file is gone
        // while it may still exist in R2.
        storageService.delete(asset.getStorageKey());

        asset.setStorageKey(null);
        asset.setFileSizeBytes(null);
        asset.setAudioFormat(null);
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    // Never derived from client input beyond the validated extension — userId/assetId come from
    // the authenticated principal and the already-ownership-checked Asset, and the random UUID
    // segment guarantees two uploads never collide even if replacing the same asset's file
    // repeatedly. No path traversal is possible since none of these segments are raw client text.
    private String buildStorageKey(UUID ownerId, UUID assetId, String extension) {
        return "users/%s/assets/%s/%s.%s".formatted(ownerId, assetId, UUID.randomUUID(), extension);
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
