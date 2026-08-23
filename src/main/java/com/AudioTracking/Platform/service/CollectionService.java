package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.collection.CollectionResponse;
import com.AudioTracking.Platform.dto.collection.CreateCollectionRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface CollectionService {

    CollectionResponse createCollection(UUID ownerId, CreateCollectionRequest request);

    List<CollectionResponse> getCollections(UUID ownerId, Sort sort);

    CollectionResponse getCollection(UUID ownerId, UUID collectionId);

    CollectionResponse updateCollection(UUID ownerId, UUID collectionId, CreateCollectionRequest request);

    void deleteCollection(UUID ownerId, UUID collectionId);

    CollectionResponse addAsset(UUID ownerId, UUID collectionId, UUID assetId);

    CollectionResponse removeAsset(UUID ownerId, UUID collectionId, UUID assetId);
}
