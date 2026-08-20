package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.asset.CreateAssetRequest;
import com.AudioTracking.Platform.dto.asset.UpdateAssetRequest;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.AssetType;
import com.AudioTracking.Platform.entity.Collection;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.Tag;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.exception.StorageException;
import com.AudioTracking.Platform.mapper.AssetMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock private AssetRepository assetRepository;
    @Mock private UserRepository userRepository;
    @Mock private TagRepository tagRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private StorageService storageService;
    @Mock private AssetMapper assetMapper;

    private AssetServiceImpl assetService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assetService = new AssetServiceImpl(assetRepository, userRepository, tagRepository, projectRepository,
                storageService, assetMapper, 15L);
    }

    @Test
    void createAsset_assignsAuthenticatedUserAsOwner_regardlessOfRequestContent() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset mapped = new Asset();
        when(assetMapper.toEntity(request)).thenReturn(mapped);

        User ownerRef = new User();
        ownerRef.setId(ownerId);
        when(userRepository.getReferenceById(ownerId)).thenReturn(ownerRef);

        Asset saved = new Asset();
        saved.setId(assetId);
        when(assetRepository.save(mapped)).thenReturn(saved);

        AssetResponse expectedResponse = mock(AssetResponse.class);
        when(assetMapper.toResponse(saved)).thenReturn(expectedResponse);

        AssetResponse result = assetService.createAsset(ownerId, request);

        assertThat(result).isSameAs(expectedResponse);
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(ownerRef);
    }

    @Test
    void getAsset_ownedByCaller_returnsIt() {
        Asset asset = new Asset();
        asset.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);

        assertThat(assetService.getAsset(ownerId, assetId)).isSameAs(expected);
    }

    @Test
    void getAsset_notOwnedByCaller_throwsNotFound_sameAsNonexistentId() {
        // findByIdAndUserId is the whole ownership boundary: a wrong id and someone else's
        // asset id must produce the exact same failure, so existence is never leaked.
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.getAsset(otherUserId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAsset_notOwnedByCaller_throwsNotFound_andNeverWrites() {
        UpdateAssetRequest request = new UpdateAssetRequest("New title", null, AssetType.BEAT, null, null, null, null, null, null);
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.updateAsset(otherUserId, assetId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetMapper, never()).updateEntity(any(), any());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void updateAsset_ownedByCaller_appliesMapperToExistingEntityAndSaves() {
        UpdateAssetRequest request = new UpdateAssetRequest("New title", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset existing = new Asset();
        existing.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(existing)).thenReturn(expected);

        AssetResponse result = assetService.updateAsset(ownerId, assetId, request);

        assertThat(result).isSameAs(expected);
        verify(assetMapper).updateEntity(request, existing);
        verify(assetRepository).save(existing);
    }

    @Test
    void deleteAsset_notOwnedByCaller_throwsNotFound_andNeverDeletes() {
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.deleteAsset(otherUserId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetRepository, never()).delete(any());
    }

    @Test
    void deleteAsset_ownedByCaller_deletesIt() {
        Asset existing = new Asset();
        existing.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_removesItFromEveryCollectionItBelongedTo_withoutDeletingThoseCollections() {
        Asset existing = new Asset();
        existing.setId(assetId);

        // Distinct ids matter here: equality is id-based, so two unsaved (id == null) entities
        // would otherwise be treated as "the same collection" and collide inside the Set.
        Collection collectionA = new Collection();
        collectionA.setId(UUID.randomUUID());
        Collection collectionB = new Collection();
        collectionB.setId(UUID.randomUUID());
        collectionA.addAsset(existing);
        collectionB.addAsset(existing);

        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        assertThat(collectionA.getAssets()).isEmpty();
        assertThat(collectionB.getAssets()).isEmpty();
        assertThat(existing.getCollections()).isEmpty();
        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_withStorageKey_alsoDeletesTheStorageObject() {
        Asset existing = new Asset();
        existing.setId(assetId);
        existing.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        verify(storageService).delete("users/x/assets/y/z.wav");
        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_withNoStorageKey_neverCallsStorage() {
        Asset existing = new Asset();
        existing.setId(assetId); // storageKey left null
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        verify(storageService, never()).delete(any());
    }

    @Test
    void deleteAsset_storageDeleteFails_stillDeletesTheAssetAnyway() {
        // Best-effort by design: the asset is being removed regardless, so a storage-provider
        // hiccup must not block the user from deleting their own asset.
        Asset existing = new Asset();
        existing.setId(assetId);
        existing.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));
        doThrow(new StorageException("boom", new RuntimeException()))
                .when(storageService).delete("users/x/assets/y/z.wav");

        assetService.deleteAsset(ownerId, assetId); // must not throw

        verify(assetRepository).delete(existing);
    }

    @Test
    void addTag_ownedAssetAndOwnedTag_addsItAndSaves() {
        Asset asset = new Asset();
        asset.setId(assetId);
        Tag tag = new Tag();
        tag.setId(tagId);

        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(tagRepository.findByIdAndUserId(tagId, ownerId)).thenReturn(Optional.of(tag));
        when(assetRepository.save(asset)).thenReturn(asset);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);

        AssetResponse result = assetService.addTag(ownerId, assetId, tagId);

        assertThat(result).isSameAs(expected);
        assertThat(asset.getTags()).containsExactly(tag);
    }

    @Test
    void addTag_tagBelongsToAnotherUser_throwsNotFound_andNeverAssociates() {
        Asset asset = new Asset();
        asset.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(tagRepository.findByIdAndUserId(tagId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.addTag(ownerId, assetId, tagId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetRepository, never()).save(any());
    }

    @Test
    void addTag_assetBelongsToAnotherUser_throwsNotFound() {
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.addTag(otherUserId, assetId, tagId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tagRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void addTag_alreadyAssociated_isANoOp_setStaysSizeOne() {
        Asset asset = new Asset();
        asset.setId(assetId);
        Tag tag = new Tag();
        tag.setId(tagId);
        asset.getTags().add(tag); // already attached

        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(tagRepository.findByIdAndUserId(tagId, ownerId)).thenReturn(Optional.of(tag));
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(mock(AssetResponse.class));

        assetService.addTag(ownerId, assetId, tagId);

        assertThat(asset.getTags()).hasSize(1);
    }

    @Test
    void removeTag_attached_removesIt() {
        Asset asset = new Asset();
        asset.setId(assetId);
        Tag tag = new Tag();
        tag.setId(tagId);
        asset.getTags().add(tag);

        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);

        AssetResponse result = assetService.removeTag(ownerId, assetId, tagId);

        assertThat(result).isSameAs(expected);
        assertThat(asset.getTags()).isEmpty();
    }

    @Test
    void removeTag_notCurrentlyAttached_isANoOp_notAnError() {
        Asset asset = new Asset();
        asset.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(mock(AssetResponse.class));

        assetService.removeTag(ownerId, assetId, tagId); // does not throw

        assertThat(asset.getTags()).isEmpty();
    }

    @Test
    void removeTag_assetBelongsToAnotherUser_throwsNotFound() {
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.removeTag(otherUserId, assetId, tagId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createAsset_withOwnedProjectId_assignsIt() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, projectId);
        Asset mapped = new Asset();
        when(assetMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());

        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(project));
        when(assetRepository.save(mapped)).thenReturn(mapped);
        when(assetMapper.toResponse(mapped)).thenReturn(mock(AssetResponse.class));

        assetService.createAsset(ownerId, request);

        assertThat(mapped.getProject()).isSameAs(project);
    }

    @Test
    void createAsset_withProjectIdBelongingToAnotherUser_throwsNotFound_andNeverSaves() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, projectId);
        when(assetMapper.toEntity(request)).thenReturn(new Asset());
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());
        when(projectRepository.findByIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.createAsset(ownerId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetRepository, never()).save(any());
    }

    @Test
    void createAsset_withNullProjectId_leavesProjectUnset() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset mapped = new Asset();
        when(assetMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());
        when(assetRepository.save(mapped)).thenReturn(mapped);
        when(assetMapper.toResponse(mapped)).thenReturn(mock(AssetResponse.class));

        assetService.createAsset(ownerId, request);

        assertThat(mapped.getProject()).isNull();
        verify(projectRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void updateAsset_omittingProjectId_unassignsExistingProject() {
        UpdateAssetRequest request = new UpdateAssetRequest("Title", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset existing = new Asset();
        existing.setId(assetId);
        existing.setProject(new Project()); // previously assigned to some project
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        when(assetMapper.toResponse(existing)).thenReturn(mock(AssetResponse.class));

        assetService.updateAsset(ownerId, assetId, request);

        assertThat(existing.getProject()).isNull();
    }

    // A syntactically valid minimal WAV header: "RIFF" + 4 arbitrary size bytes + "WAVE".
    // AudioFileValidator only checks the signature bytes, not the rest of the file structure.
    private static final byte[] VALID_WAV_BYTES = "RIFF1234WAVEfmt ".getBytes();

    @Test
    void uploadFile_firstUpload_setsStorageKeyAndMetadata_noOldObjectToClean() throws Exception {
        Asset asset = new Asset();
        asset.setId(assetId); // storageKey starts null
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(mock(AssetResponse.class));

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        assetService.uploadFile(ownerId, assetId, file);

        assertThat(asset.getStorageKey()).contains("users/" + ownerId).contains("assets/" + assetId).endsWith(".wav");
        assertThat(asset.getFileSizeBytes()).isEqualTo((long) VALID_WAV_BYTES.length);
        assertThat(asset.getAudioFormat()).isEqualTo("wav");
        verify(storageService).upload(anyString(), any(), eq((long) VALID_WAV_BYTES.length), eq("audio/wav"));
        verify(storageService, never()).delete(anyString()); // nothing to replace
    }

    @Test
    void uploadFile_replacingExisting_uploadsNewThenDeletesOld_inThatOrder() throws Exception {
        Asset asset = new Asset();
        asset.setId(assetId);
        asset.setStorageKey("users/x/assets/y/old.wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(mock(AssetResponse.class));

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        assetService.uploadFile(ownerId, assetId, file);

        assertThat(asset.getStorageKey()).isNotEqualTo("users/x/assets/y/old.wav");
        var inOrder = org.mockito.Mockito.inOrder(storageService, assetRepository);
        inOrder.verify(storageService).upload(anyString(), any(), anyLong(), anyString());
        inOrder.verify(assetRepository).save(asset);
        inOrder.verify(storageService).delete("users/x/assets/y/old.wav");
    }

    @Test
    void uploadFile_replaceOldObjectDeleteFails_stillSucceeds() throws Exception {
        // Best-effort cleanup: the asset already correctly points at the new file by this point.
        Asset asset = new Asset();
        asset.setId(assetId);
        asset.setStorageKey("users/x/assets/y/old.wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);
        doThrow(new StorageException("boom", new RuntimeException())).when(storageService).delete("users/x/assets/y/old.wav");

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        AssetResponse result = assetService.uploadFile(ownerId, assetId, file); // must not throw

        assertThat(result).isSameAs(expected);
    }

    @Test
    void uploadFile_assetNotOwnedByCaller_throwsNotFound_neverTouchesStorage() {
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        assertThatThrownBy(() -> assetService.uploadFile(otherUserId, assetId, file))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadFile_invalidFile_throwsBeforeTouchingStorageOrDatabase() {
        Asset asset = new Asset();
        asset.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));

        MultipartFile badFile = new MockMultipartFile("file", "not-audio.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> assetService.uploadFile(ownerId, assetId, badFile))
                .isInstanceOf(com.AudioTracking.Platform.exception.InvalidFileException.class);

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void getFileAccessUrl_ownedAssetWithFile_returnsPresignedUrl() {
        Asset asset = new Asset();
        asset.setId(assetId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        java.net.URI presigned = java.net.URI.create("https://r2.example.com/signed?sig=abc");
        when(storageService.generatePresignedDownloadUrl(eq("users/x/assets/y/z.wav"), any())).thenReturn(presigned);

        var response = assetService.getFileAccessUrl(ownerId, assetId);

        assertThat(response.url()).isEqualTo(presigned.toString());
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void getFileAccessUrl_assetHasNoFile_throwsNotFound() {
        Asset asset = new Asset();
        asset.setId(assetId); // storageKey null
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.getFileAccessUrl(ownerId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getFileAccessUrl_assetNotOwnedByCaller_throwsNotFound() {
        when(assetRepository.findByIdAndUserId(assetId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.getFileAccessUrl(otherUserId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteFile_ownedAssetWithFile_clearsStorageMetadata() {
        Asset asset = new Asset();
        asset.setId(assetId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        asset.setFileSizeBytes(123L);
        asset.setAudioFormat("wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(mock(AssetResponse.class));

        assetService.deleteFile(ownerId, assetId);

        verify(storageService).delete("users/x/assets/y/z.wav");
        assertThat(asset.getStorageKey()).isNull();
        assertThat(asset.getFileSizeBytes()).isNull();
        assertThat(asset.getAudioFormat()).isNull();
    }

    @Test
    void deleteFile_storageDeleteFails_propagatesAndLeavesAssetUntouched() {
        // Unlike deleteAsset/uploadFile's old-object cleanup, THIS is the user's explicit
        // "remove the file" request — a failure here must not be swallowed, since silently
        // clearing the DB reference could leave the file stranded in R2 with no way to find it again.
        Asset asset = new Asset();
        asset.setId(assetId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));
        doThrow(new StorageException("boom", new RuntimeException())).when(storageService).delete("users/x/assets/y/z.wav");

        assertThatThrownBy(() -> assetService.deleteFile(ownerId, assetId)).isInstanceOf(StorageException.class);

        assertThat(asset.getStorageKey()).isEqualTo("users/x/assets/y/z.wav"); // untouched
        verify(assetRepository, never()).save(any());
    }

    @Test
    void deleteFile_assetHasNoFile_throwsNotFound() {
        Asset asset = new Asset();
        asset.setId(assetId);
        when(assetRepository.findByIdAndUserId(assetId, ownerId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.deleteFile(ownerId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
