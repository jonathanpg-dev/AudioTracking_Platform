package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.asset.AssetFilter;
import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.FileAccessResponse;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import org.springframework.data.domain.Pageable;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Collection;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.Tag;
import com.AudioTracking.Platform.exception.InvalidFileException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.exception.StorageException;
import com.AudioTracking.Platform.mapper.AssetMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.AssetService;
import com.AudioTracking.Platform.service.ProjectAccessService;
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
    private final ProjectAccessService projectAccessService;
    private final StorageService storageService;
    private final AnalyticsService analyticsService;
    private final AssetMapper assetMapper;
    private final long presignedUrlExpirationMinutes;

    public AssetServiceImpl(AssetRepository assetRepository, UserRepository userRepository,
                             TagRepository tagRepository, ProjectAccessService projectAccessService,
                             StorageService storageService, AnalyticsService analyticsService,
                             AssetMapper assetMapper,
                             @Value("${storage.presigned-url-expiration-minutes}") long presignedUrlExpirationMinutes) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.projectAccessService = projectAccessService;
        this.storageService = storageService;
        this.analyticsService = analyticsService;
        this.assetMapper = assetMapper;
        this.presignedUrlExpirationMinutes = presignedUrlExpirationMinutes;
    }

    @Override
    public AssetResponse createAsset(UUID requesterId, CreateAssetRequest request) {
        Asset asset = assetMapper.toEntity(request);
        // The creator is always the owner -- even when the created asset is going straight into
        // someone else's shared project (an EDIT collaborator adding a resource). Sharing never
        // transfers ownership; see docs/collaboration.md.
        // getReferenceById avoids an extra SELECT: the caller is already an authenticated
        // user resolved from the JWT, so we only need their id to set the FK, not the full row.
        asset.setUser(userRepository.getReferenceById(requesterId));
        asset.setProject(resolveAssignableProjectOrNull(requesterId, request.projectId()));
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
    public AssetResponse getAsset(UUID requesterId, UUID assetId) {
        return assetMapper.toResponse(findAccessibleOrThrow(requesterId, assetId, RequiredAccess.VIEW));
    }

    @Override
    public AssetResponse updateAsset(UUID requesterId, UUID assetId, UpdateAssetRequest request) {
        Asset existing = findAccessibleOrThrow(requesterId, assetId, RequiredAccess.EDIT);
        assetMapper.updateEntity(request, existing);
        // Full-replace semantics like every other field on this DTO: omitting projectId (or
        // sending null) unassigns the asset from whatever project it was previously in. Ownership
        // (existing.user) is deliberately never touched here -- a collaborator editing this asset
        // never becomes its owner.
        existing.setProject(resolveAssignableProjectOrNull(requesterId, request.projectId()));
        return assetMapper.toResponse(assetRepository.save(existing));
    }

    @Override
    @Transactional // collection-membership cleanup + the delete itself must succeed together
    public void deleteAsset(UUID requesterId, UUID assetId) {
        Asset existing = findAccessibleOrThrow(requesterId, assetId, RequiredAccess.EDIT);
        // Captured before the delete below: the event needs this snapshot regardless of whether
        // the in-memory entity stays readable afterward (plain UUID, not a live relationship --
        // see AnalyticsEvent.projectId).
        UUID projectId = projectIdOf(existing);
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
        analyticsService.record(requesterId, AnalyticsEventType.ASSET_DELETED, assetId, projectId);
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
    public AssetResponse uploadFile(UUID requesterId, UUID assetId, MultipartFile file) {
        Asset asset = findAccessibleOrThrow(requesterId, assetId, RequiredAccess.EDIT);
        AudioFileValidator.ValidatedAudioFile validated = AudioFileValidator.validate(file);

        String previousKey = asset.getStorageKey(); // null if this is a first-time upload
        // Namespaced under the ASSET'S owner, not necessarily the requester -- an EDIT
        // collaborator uploading into someone else's asset must not scatter objects under their
        // own "users/{id}/..." prefix; the key's namespace stays stable regardless of who acts on it.
        String newKey = buildStorageKey(asset.getUser().getId(), assetId, validated.extension());

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
        analyticsService.record(requesterId, AnalyticsEventType.ASSET_UPLOADED, assetId, projectIdOf(saved));

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
    public FileAccessResponse getFileAccessUrl(UUID requesterId, UUID assetId, boolean download) {
        Asset asset = findAccessibleOrThrow(requesterId, assetId, RequiredAccess.VIEW);
        if (asset.getStorageKey() == null) {
            throw new ResourceNotFoundException("Asset with id '" + assetId + "' has no associated audio file");
        }

        Duration expiration = Duration.ofMinutes(presignedUrlExpirationMinutes);
        URI url = storageService.generatePresignedDownloadUrl(asset.getStorageKey(), expiration);
        // `download` only ever changes which analytics event gets recorded, never the access
        // check or the URL itself -- see AssetService#getFileAccessUrl and docs/analytics.md.
        AnalyticsEventType eventType = download ? AnalyticsEventType.ASSET_DOWNLOADED : AnalyticsEventType.ASSET_PLAYED;
        analyticsService.record(requesterId, eventType, assetId, projectIdOf(asset));
        return new FileAccessResponse(url.toString(), Instant.now().plus(expiration));
    }

    @Override
    public AssetResponse deleteFile(UUID requesterId, UUID assetId) {
        Asset asset = findAccessibleOrThrow(requesterId, assetId, RequiredAccess.EDIT);
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

    @Override
    public List<AssetResponse> getProjectAssets(UUID requesterId, UUID projectId) {
        // Owner, VIEW, or EDIT collaborator can all browse a Project's assets.
        projectAccessService.requireViewAccess(requesterId, projectId);
        return assetMapper.toResponseList(assetRepository.findAllByProjectId(projectId));
    }

    // Snapshot helper for analytics events: null-safe extraction of an Asset's current project id.
    private UUID projectIdOf(Asset asset) {
        return asset.getProject() == null ? null : asset.getProject().getId();
    }

    // Never derived from client input beyond the validated extension — userId/assetId come from
    // the authenticated principal and the already-ownership-checked Asset, and the random UUID
    // segment guarantees two uploads never collide even if replacing the same asset's file
    // repeatedly. No path traversal is possible since none of these segments are raw client text.
    private String buildStorageKey(UUID ownerId, UUID assetId, String extension) {
        return "users/%s/assets/%s/%s.%s".formatted(ownerId, assetId, UUID.randomUUID(), extension);
    }

    // Strictly owner-only -- used only by addTag/removeTag above. Tags are their own
    // independently-owned resource with no project relationship at all (see Tag.java), so Phase 5
    // collaboration deliberately does NOT extend to them: a collaborator would need to reference
    // tags they don't own, which raises whose-tag-is-it questions this app doesn't have an answer
    // for yet. See docs/collaboration.md.
    private Asset findOwnedOrThrow(UUID ownerId, UUID assetId) {
        return assetRepository.findByIdAndUserId(assetId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset with id '" + assetId + "' not found"));
    }

    private enum RequiredAccess { VIEW, EDIT }

    // The collaboration-aware counterpart to findOwnedOrThrow above: succeeds for the asset's
    // owner OR a sufficiently-permissioned collaborator on the asset's Project. Asset ownership
    // itself is completely unaffected by this -- it only ever governs what THIS request may do,
    // never who owns the row. See docs/collaboration.md.
    private Asset findAccessibleOrThrow(UUID requesterId, UUID assetId, RequiredAccess required) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset with id '" + assetId + "' not found"));

        if (asset.getUser().getId().equals(requesterId)) {
            return asset; // owner: full access regardless of `required`
        }

        if (asset.getProject() == null) {
            // Not owned by the requester and not part of any project -> nothing to share
            // through. Same 404 an unrelated user gets for a nonexistent asset.
            throw new ResourceNotFoundException("Asset with id '" + assetId + "' not found");
        }

        // Delegates the actual permission decision to ProjectAccessService so it lives in exactly
        // one place; propagates its ResourceNotFoundException (unrelated to the project) or
        // InsufficientPermissionException (e.g. a VIEW collaborator attempting an EDIT-required
        // operation) as-is.
        UUID projectId = asset.getProject().getId();
        if (required == RequiredAccess.EDIT) {
            projectAccessService.requireEditAccess(requesterId, projectId);
        } else {
            projectAccessService.requireViewAccess(requesterId, projectId);
        }
        return asset;
    }

    // null projectId means "no project" — not an error. Requires EDIT access (owner or EDIT
    // collaborator) on the target project: matches the EDIT permission's "can add/modify
    // resources in the Project" and prevents moving an asset into a project the requester can
    // only view, or has no relationship to at all -- a wrong id and an unauthorized real id both
    // produce the same error, never revealing whether the project exists.
    private Project resolveAssignableProjectOrNull(UUID requesterId, UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return projectAccessService.requireEditAccess(requesterId, projectId);
    }
}
