package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.collection.CollectionResponse;
import com.AudioTracking.Platform.dto.collection.CreateCollectionRequest;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Collection;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.CollectionMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.CollectionRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AnalyticsService analyticsService;
    @Mock private CollectionMapper collectionMapper;

    private CollectionServiceImpl collectionService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID collectionId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        collectionService = new CollectionServiceImpl(collectionRepository, userRepository, assetRepository, analyticsService, collectionMapper);
    }

    @Test
    void createCollection_assignsAuthenticatedUserAsOwner() {
        CreateCollectionRequest request = new CreateCollectionRequest("Favorites");
        Collection mapped = new Collection();
        when(collectionMapper.toEntity(request)).thenReturn(mapped);

        User ownerRef = new User();
        ownerRef.setId(ownerId);
        when(userRepository.getReferenceById(ownerId)).thenReturn(ownerRef);
        when(collectionRepository.save(mapped)).thenReturn(mapped);
        when(collectionMapper.toResponse(mapped)).thenReturn(mock(CollectionResponse.class));

        collectionService.createCollection(ownerId, request);

        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(ownerRef);
    }

    @Test
    void getCollection_notOwnedByCaller_throwsNotFound_sameAsNonexistentId() {
        when(collectionRepository.findByIdAndUserId(collectionId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.getCollection(otherUserId, collectionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCollection_ownedByCaller_deletesIt() {
        Collection existing = new Collection();
        existing.setId(collectionId);
        when(collectionRepository.findByIdAndUserId(collectionId, ownerId)).thenReturn(Optional.of(existing));

        collectionService.deleteCollection(ownerId, collectionId);

        verify(collectionRepository).delete(existing);
    }

    @Test
    void addAsset_ownedCollectionAndOwnedAsset_addsItAndSaves() {
        Collection collection = new Collection();
        collection.setId(collectionId);
        Asset asset = new Asset();
        asset.setId(assetId);

        when(collectionRepository.findByIdAndUserId(collectionId, ownerId)).thenReturn(Optional.of(collection));
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(collectionRepository.save(collection)).thenReturn(collection);
        when(collectionMapper.toResponse(collection)).thenReturn(mock(CollectionResponse.class));

        collectionService.addAsset(ownerId, collectionId, assetId);

        assertThat(collection.getAssets()).containsExactly(asset);
        assertThat(asset.getCollections()).containsExactly(collection);
    }

    @Test
    void addAsset_assetBelongsToAnotherUser_throwsNotFound_andNeverAssociates() {
        Collection collection = new Collection();
        collection.setId(collectionId);
        when(collectionRepository.findByIdAndUserId(collectionId, ownerId)).thenReturn(Optional.of(collection));
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.addAsset(ownerId, collectionId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(collectionRepository, never()).save(any());
    }

    @Test
    void addAsset_collectionBelongsToAnotherUser_throwsNotFound() {
        when(collectionRepository.findByIdAndUserId(collectionId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.addAsset(otherUserId, collectionId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void addAsset_alreadyInCollection_isANoOp_setStaysSizeOne() {
        Collection collection = new Collection();
        collection.setId(collectionId);
        Asset asset = new Asset();
        asset.setId(assetId);
        collection.addAsset(asset); // already in it

        when(collectionRepository.findByIdAndUserId(collectionId, ownerId)).thenReturn(Optional.of(collection));
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(collectionRepository.save(collection)).thenReturn(collection);
        when(collectionMapper.toResponse(collection)).thenReturn(mock(CollectionResponse.class));

        collectionService.addAsset(ownerId, collectionId, assetId);

        assertThat(collection.getAssets()).hasSize(1);
    }

    @Test
    void removeAsset_present_removesIt() {
        Collection collection = new Collection();
        collection.setId(collectionId);
        Asset asset = new Asset();
        asset.setId(assetId);
        collection.addAsset(asset);

        when(collectionRepository.findByIdAndUserId(collectionId, ownerId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenReturn(collection);
        when(collectionMapper.toResponse(collection)).thenReturn(mock(CollectionResponse.class));

        collectionService.removeAsset(ownerId, collectionId, assetId);

        assertThat(collection.getAssets()).isEmpty();
        assertThat(asset.getCollections()).isEmpty();
    }

    @Test
    void removeAsset_notCurrentlyPresent_isANoOp_notAnError() {
        Collection collection = new Collection();
        collection.setId(collectionId);
        when(collectionRepository.findByIdAndUserId(collectionId, ownerId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(collection)).thenReturn(collection);
        when(collectionMapper.toResponse(collection)).thenReturn(mock(CollectionResponse.class));

        collectionService.removeAsset(ownerId, collectionId, assetId); // does not throw

        assertThat(collection.getAssets()).isEmpty();
    }
}
