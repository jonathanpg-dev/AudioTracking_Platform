package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.collection.CollectionResponse;
import com.AudioTracking.Platform.dto.collection.CreateCollectionRequest;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Collection;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.CollectionMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.CollectionRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.CollectionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CollectionServiceImpl implements CollectionService {

    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AnalyticsService analyticsService;
    private final CollectionMapper collectionMapper;

    public CollectionServiceImpl(CollectionRepository collectionRepository, UserRepository userRepository,
                                  AssetRepository assetRepository, AnalyticsService analyticsService,
                                  CollectionMapper collectionMapper) {
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.analyticsService = analyticsService;
        this.collectionMapper = collectionMapper;
    }

    @Override
    public CollectionResponse createCollection(UUID ownerId, CreateCollectionRequest request) {
        Collection collection = collectionMapper.toEntity(request);
        collection.setUser(userRepository.getReferenceById(ownerId));
        Collection saved = collectionRepository.save(collection);
        analyticsService.record(ownerId, AnalyticsEventType.COLLECTION_CREATED, null, null);
        return collectionMapper.toResponse(saved);
    }

    @Override
    public List<CollectionResponse> getCollections(UUID ownerId) {
        return collectionMapper.toResponseList(collectionRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId));
    }

    @Override
    public CollectionResponse getCollection(UUID ownerId, UUID collectionId) {
        return collectionMapper.toResponse(findOwnedOrThrow(ownerId, collectionId));
    }

    @Override
    public CollectionResponse updateCollection(UUID ownerId, UUID collectionId, CreateCollectionRequest request) {
        Collection existing = findOwnedOrThrow(ownerId, collectionId);
        existing.setName(request.name());
        return collectionMapper.toResponse(collectionRepository.save(existing));
    }

    @Override
    public void deleteCollection(UUID ownerId, UUID collectionId) {
        // Collection owns the relationship, so Hibernate cleans up collection_assets rows
        // automatically on delete — no explicit association cleanup needed here (contrast with
        // AssetServiceImpl#deleteAsset, which is the non-owning side).
        Collection existing = findOwnedOrThrow(ownerId, collectionId);
        collectionRepository.delete(existing);
    }

    @Override
    public CollectionResponse addAsset(UUID ownerId, UUID collectionId, UUID assetId) {
        Collection collection = findOwnedOrThrow(ownerId, collectionId);
        // Ownership check on the asset too: a user must never be able to add someone else's
        // asset into their own collection. Same "wrong id and someone else's id look identical"
        // 404 used everywhere else in this API.
        Asset asset = assetRepository.findByIdAndUserId(assetId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset with id '" + assetId + "' not found"));

        collection.addAsset(asset); // keeps both sides of the bidirectional collection in sync; no-op if already added
        return collectionMapper.toResponse(collectionRepository.save(collection));
    }

    @Override
    public CollectionResponse removeAsset(UUID ownerId, UUID collectionId, UUID assetId) {
        Collection collection = findOwnedOrThrow(ownerId, collectionId);
        // No separate asset-ownership check needed here: collection.getAssets() can only ever
        // contain assets this user owns in the first place (addAsset enforces that), so
        // filtering by id alone is safe. Removing an asset that isn't in it is a no-op.
        collection.getAssets().stream()
                .filter(asset -> asset.getId().equals(assetId))
                .findFirst()
                .ifPresent(collection::removeAsset);
        return collectionMapper.toResponse(collectionRepository.save(collection));
    }

    private Collection findOwnedOrThrow(UUID ownerId, UUID collectionId) {
        return collectionRepository.findByIdAndUserId(collectionId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection with id '" + collectionId + "' not found"));
    }
}
