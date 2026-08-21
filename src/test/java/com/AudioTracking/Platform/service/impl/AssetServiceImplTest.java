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
import com.AudioTracking.Platform.exception.InsufficientPermissionException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.exception.StorageException;
import com.AudioTracking.Platform.mapper.AssetMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.ProjectAccessService;
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
    @Mock private ProjectAccessService projectAccessService;
    @Mock private StorageService storageService;
    @Mock private AnalyticsService analyticsService;
    @Mock private AssetMapper assetMapper;

    private AssetServiceImpl assetService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assetService = new AssetServiceImpl(assetRepository, userRepository, tagRepository,
                projectAccessService, storageService, analyticsService, assetMapper, 15L);
    }

    private static User userWithId(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    // Asset owned by `ownerId`, with no Project -- the baseline "plain ownership" fixture used by
    // most tests below. findAccessibleOrThrow resolves ownership by asset.getUser().getId(), so
    // every asset fixture from here on needs a User set, unlike pre-Phase-5 tests that only
    // needed assetRepository.findByIdAndUserId to be stubbed correctly.
    private static Asset ownedAsset(UUID id, UUID ownerId) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setUser(userWithId(ownerId));
        return asset;
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
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);

        assertThat(assetService.getAsset(ownerId, assetId)).isSameAs(expected);
    }

    @Test
    void getAsset_notOwnedByCaller_noProjectToShareThrough_throwsNotFound() {
        // Owned by someone else, not part of any Project -- nothing for otherUserId to access
        // through, same failure as a nonexistent asset.
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.getAsset(otherUserId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAsset_nonexistentId_throwsNotFound() {
        when(assetRepository.findById(assetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.getAsset(ownerId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- Collaboration: VIEW/EDIT access through a shared Project ---

    @Test
    void getAsset_viewCollaboratorOnAssetsProject_canView() {
        Project project = new Project();
        project.setId(projectId);
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setProject(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(projectAccessService.requireViewAccess(otherUserId, projectId)).thenReturn(project);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);

        assertThat(assetService.getAsset(otherUserId, assetId)).isSameAs(expected);
    }

    @Test
    void getAsset_unrelatedToAssetsProject_throwsNotFound() {
        Project project = new Project();
        project.setId(projectId);
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setProject(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(projectAccessService.requireViewAccess(otherUserId, projectId))
                .thenThrow(new ResourceNotFoundException("Project with id '" + projectId + "' not found"));

        assertThatThrownBy(() -> assetService.getAsset(otherUserId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAsset_viewCollaborator_isRejected_editRequired() {
        Project project = new Project();
        project.setId(projectId);
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setProject(project);
        UpdateAssetRequest request = new UpdateAssetRequest("New title", null, AssetType.BEAT, null, null, null, null, null, null);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(projectAccessService.requireEditAccess(otherUserId, projectId))
                .thenThrow(new InsufficientPermissionException("VIEW collaborators cannot perform this action"));

        assertThatThrownBy(() -> assetService.updateAsset(otherUserId, assetId, request))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(assetRepository, never()).save(any());
    }

    @Test
    void updateAsset_editCollaborator_canModify_ownershipUnchanged() {
        Project project = new Project();
        project.setId(projectId);
        Asset existing = ownedAsset(assetId, ownerId); // still owned by the original creator
        existing.setProject(project);
        UpdateAssetRequest request = new UpdateAssetRequest("New title", null, AssetType.BEAT, null, null, null, null, null, null);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));
        when(projectAccessService.requireEditAccess(otherUserId, projectId)).thenReturn(project);
        when(assetRepository.save(existing)).thenReturn(existing);
        when(assetMapper.toResponse(existing)).thenReturn(mock(AssetResponse.class));

        assetService.updateAsset(otherUserId, assetId, request);

        verify(assetMapper).updateEntity(request, existing);
        // The EDIT collaborator modified it, but ownership never moved off the original creator.
        assertThat(existing.getUser().getId()).isEqualTo(ownerId);
    }

    @Test
    void deleteAsset_editCollaborator_canDelete() {
        Project project = new Project();
        project.setId(projectId);
        Asset existing = ownedAsset(assetId, ownerId);
        existing.setProject(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));
        when(projectAccessService.requireEditAccess(otherUserId, projectId)).thenReturn(project);

        assetService.deleteAsset(otherUserId, assetId);

        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_viewCollaborator_isRejected() {
        Project project = new Project();
        project.setId(projectId);
        Asset existing = ownedAsset(assetId, ownerId);
        existing.setProject(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));
        when(projectAccessService.requireEditAccess(otherUserId, projectId))
                .thenThrow(new InsufficientPermissionException("VIEW collaborators cannot perform this action"));

        assertThatThrownBy(() -> assetService.deleteAsset(otherUserId, assetId))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(assetRepository, never()).delete(any());
    }

    @Test
    void getProjectAssets_requiresViewAccess_returnsMappedList() {
        when(projectAccessService.requireViewAccess(ownerId, projectId)).thenReturn(new Project());
        Asset a1 = new Asset();
        when(assetRepository.findAllByProjectId(projectId)).thenReturn(java.util.List.of(a1));
        java.util.List<AssetResponse> expected = java.util.List.of(mock(AssetResponse.class));
        when(assetMapper.toResponseList(java.util.List.of(a1))).thenReturn(expected);

        assertThat(assetService.getProjectAssets(ownerId, projectId)).isSameAs(expected);
    }

    @Test
    void getProjectAssets_unrelatedUser_throwsNotFound() {
        when(projectAccessService.requireViewAccess(otherUserId, projectId))
                .thenThrow(new ResourceNotFoundException("Project with id '" + projectId + "' not found"));

        assertThatThrownBy(() -> assetService.getProjectAssets(otherUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- Update/delete: plain ownership path (no Project involved) ---

    @Test
    void updateAsset_notOwnedByCaller_noProject_throwsNotFound_andNeverWrites() {
        UpdateAssetRequest request = new UpdateAssetRequest("New title", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.updateAsset(otherUserId, assetId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetMapper, never()).updateEntity(any(), any());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void updateAsset_ownedByCaller_appliesMapperToExistingEntityAndSaves() {
        UpdateAssetRequest request = new UpdateAssetRequest("New title", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset existing = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));
        when(assetRepository.save(existing)).thenReturn(existing);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(existing)).thenReturn(expected);

        AssetResponse result = assetService.updateAsset(ownerId, assetId, request);

        assertThat(result).isSameAs(expected);
        verify(assetMapper).updateEntity(request, existing);
        verify(assetRepository).save(existing);
    }

    @Test
    void deleteAsset_notOwnedByCaller_noProject_throwsNotFound_andNeverDeletes() {
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.deleteAsset(otherUserId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assetRepository, never()).delete(any());
    }

    @Test
    void deleteAsset_ownedByCaller_deletesIt() {
        Asset existing = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_removesItFromEveryCollectionItBelongedTo_withoutDeletingThoseCollections() {
        Asset existing = ownedAsset(assetId, ownerId);

        // Distinct ids matter here: equality is id-based, so two unsaved (id == null) entities
        // would otherwise be treated as "the same collection" and collide inside the Set.
        Collection collectionA = new Collection();
        collectionA.setId(UUID.randomUUID());
        Collection collectionB = new Collection();
        collectionB.setId(UUID.randomUUID());
        collectionA.addAsset(existing);
        collectionB.addAsset(existing);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        assertThat(collectionA.getAssets()).isEmpty();
        assertThat(collectionB.getAssets()).isEmpty();
        assertThat(existing.getCollections()).isEmpty();
        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_withStorageKey_alsoDeletesTheStorageObject() {
        Asset existing = ownedAsset(assetId, ownerId);
        existing.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        verify(storageService).delete("users/x/assets/y/z.wav");
        verify(assetRepository).delete(existing);
    }

    @Test
    void deleteAsset_withNoStorageKey_neverCallsStorage() {
        Asset existing = ownedAsset(assetId, ownerId); // storageKey left null
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));

        assetService.deleteAsset(ownerId, assetId);

        verify(storageService, never()).delete(any());
    }

    @Test
    void deleteAsset_storageDeleteFails_stillDeletesTheAssetAnyway() {
        // Best-effort by design: the asset is being removed regardless, so a storage-provider
        // hiccup must not block the user from deleting their own asset.
        Asset existing = ownedAsset(assetId, ownerId);
        existing.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));
        doThrow(new StorageException("boom", new RuntimeException()))
                .when(storageService).delete("users/x/assets/y/z.wav");

        assetService.deleteAsset(ownerId, assetId); // must not throw

        verify(assetRepository).delete(existing);
    }

    // --- Tags: deliberately still strictly owner-only, unaffected by Phase 5 collaboration ---

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

    // --- Asset<->Project assignment: now routed through ProjectAccessService (EDIT required) ---

    @Test
    void createAsset_withEditableProjectId_assignsIt() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, projectId);
        Asset mapped = new Asset();
        when(assetMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());

        Project project = new Project();
        project.setId(projectId);
        when(projectAccessService.requireEditAccess(ownerId, projectId)).thenReturn(project);
        when(assetRepository.save(mapped)).thenReturn(mapped);
        when(assetMapper.toResponse(mapped)).thenReturn(mock(AssetResponse.class));

        assetService.createAsset(ownerId, request);

        assertThat(mapped.getProject()).isSameAs(project);
    }

    @Test
    void createAsset_editCollaborator_canAssignIntoSharedProject_createdAssetOwnedByThem() {
        // The core "EDIT collaborator adds a resource" scenario: the new Asset is owned by the
        // collaborator who created it, NOT silently reassigned to the project owner.
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, projectId);
        Asset mapped = new Asset();
        when(assetMapper.toEntity(request)).thenReturn(mapped);
        User collaboratorRef = userWithId(otherUserId);
        when(userRepository.getReferenceById(otherUserId)).thenReturn(collaboratorRef);

        Project sharedProject = new Project();
        sharedProject.setId(projectId);
        when(projectAccessService.requireEditAccess(otherUserId, projectId)).thenReturn(sharedProject);
        when(assetRepository.save(mapped)).thenReturn(mapped);
        when(assetMapper.toResponse(mapped)).thenReturn(mock(AssetResponse.class));

        assetService.createAsset(otherUserId, request);

        assertThat(mapped.getUser()).isSameAs(collaboratorRef);
        assertThat(mapped.getProject()).isSameAs(sharedProject);
    }

    @Test
    void createAsset_withProjectIdOnlyViewable_throwsInsufficientPermission_andNeverSaves() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, projectId);
        when(assetMapper.toEntity(request)).thenReturn(new Asset());
        when(userRepository.getReferenceById(otherUserId)).thenReturn(new User());
        when(projectAccessService.requireEditAccess(otherUserId, projectId))
                .thenThrow(new InsufficientPermissionException("VIEW collaborators cannot perform this action"));

        assertThatThrownBy(() -> assetService.createAsset(otherUserId, request))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(assetRepository, never()).save(any());
    }

    @Test
    void createAsset_withUnrelatedProjectId_throwsNotFound_andNeverSaves() {
        CreateAssetRequest request = new CreateAssetRequest("Beat", null, AssetType.BEAT, null, null, null, null, null, projectId);
        when(assetMapper.toEntity(request)).thenReturn(new Asset());
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());
        when(projectAccessService.requireEditAccess(ownerId, projectId))
                .thenThrow(new ResourceNotFoundException("Project with id '" + projectId + "' not found"));

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
        verify(projectAccessService, never()).requireEditAccess(any(), any());
    }

    @Test
    void updateAsset_omittingProjectId_unassignsExistingProject() {
        UpdateAssetRequest request = new UpdateAssetRequest("Title", null, AssetType.BEAT, null, null, null, null, null, null);
        Asset existing = ownedAsset(assetId, ownerId);
        existing.setProject(new Project()); // previously assigned to some project
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(existing));
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
        Asset asset = ownedAsset(assetId, ownerId); // storageKey starts null
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
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
    void uploadFile_byEditCollaborator_keysUnderTheAssetsOwner_notTheCollaborator() throws Exception {
        // The storage key's namespace must stay stable regardless of who acts on the asset --
        // otherwise a collaborator's uploads would scatter objects across the wrong prefix.
        Project project = new Project();
        project.setId(projectId);
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setProject(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(projectAccessService.requireEditAccess(otherUserId, projectId)).thenReturn(project);
        when(assetRepository.save(asset)).thenReturn(asset);
        when(assetMapper.toResponse(asset)).thenReturn(mock(AssetResponse.class));

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        assetService.uploadFile(otherUserId, assetId, file);

        assertThat(asset.getStorageKey()).contains("users/" + ownerId).doesNotContain("users/" + otherUserId);
    }

    @Test
    void uploadFile_replacingExisting_uploadsNewThenDeletesOld_inThatOrder() throws Exception {
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setStorageKey("users/x/assets/y/old.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
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
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setStorageKey("users/x/assets/y/old.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);
        AssetResponse expected = mock(AssetResponse.class);
        when(assetMapper.toResponse(asset)).thenReturn(expected);
        doThrow(new StorageException("boom", new RuntimeException())).when(storageService).delete("users/x/assets/y/old.wav");

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        AssetResponse result = assetService.uploadFile(ownerId, assetId, file); // must not throw

        assertThat(result).isSameAs(expected);
    }

    @Test
    void uploadFile_assetNotOwnedByCaller_noProject_throwsNotFound_neverTouchesStorage() {
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        assertThatThrownBy(() -> assetService.uploadFile(otherUserId, assetId, file))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadFile_viewCollaborator_isRejected_neverTouchesStorage() {
        Project project = new Project();
        project.setId(projectId);
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setProject(project);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(projectAccessService.requireEditAccess(otherUserId, projectId))
                .thenThrow(new InsufficientPermissionException("VIEW collaborators cannot perform this action"));

        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV_BYTES);
        assertThatThrownBy(() -> assetService.uploadFile(otherUserId, assetId, file))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadFile_invalidFile_throwsBeforeTouchingStorageOrDatabase() {
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        MultipartFile badFile = new MockMultipartFile("file", "not-audio.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> assetService.uploadFile(ownerId, assetId, badFile))
                .isInstanceOf(com.AudioTracking.Platform.exception.InvalidFileException.class);

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void getFileAccessUrl_ownedAssetWithFile_returnsPresignedUrl() {
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        java.net.URI presigned = java.net.URI.create("https://r2.example.com/signed?sig=abc");
        when(storageService.generatePresignedDownloadUrl(eq("users/x/assets/y/z.wav"), any())).thenReturn(presigned);

        var response = assetService.getFileAccessUrl(ownerId, assetId, false);

        assertThat(response.url()).isEqualTo(presigned.toString());
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now());
        verify(analyticsService).record(ownerId, com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_PLAYED, assetId, null);
    }

    @Test
    void getFileAccessUrl_download_recordsAssetDownloaded_notAssetPlayed() {
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(storageService.generatePresignedDownloadUrl(eq("users/x/assets/y/z.wav"), any()))
                .thenReturn(java.net.URI.create("https://r2.example.com/signed?sig=abc"));

        assetService.getFileAccessUrl(ownerId, assetId, true);

        verify(analyticsService).record(ownerId, com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_DOWNLOADED, assetId, null);
        verify(analyticsService, never()).record(any(), eq(com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_PLAYED), any(), any());
    }

    @Test
    void getFileAccessUrl_viewCollaborator_canDownload() {
        Project project = new Project();
        project.setId(projectId);
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setProject(project);
        asset.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(projectAccessService.requireViewAccess(otherUserId, projectId)).thenReturn(project);
        java.net.URI presigned = java.net.URI.create("https://r2.example.com/signed?sig=abc");
        when(storageService.generatePresignedDownloadUrl(eq("users/x/assets/y/z.wav"), any())).thenReturn(presigned);

        var response = assetService.getFileAccessUrl(otherUserId, assetId, false);

        assertThat(response.url()).isEqualTo(presigned.toString());
    }

    @Test
    void getFileAccessUrl_assetHasNoFile_throwsNotFound() {
        Asset asset = ownedAsset(assetId, ownerId); // storageKey null
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.getFileAccessUrl(ownerId, assetId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getFileAccessUrl_assetNotOwnedByCaller_noProject_throwsNotFound() {
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.getFileAccessUrl(otherUserId, assetId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteFile_ownedAssetWithFile_clearsStorageMetadata() {
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        asset.setFileSizeBytes(123L);
        asset.setAudioFormat("wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
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
        Asset asset = ownedAsset(assetId, ownerId);
        asset.setStorageKey("users/x/assets/y/z.wav");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        doThrow(new StorageException("boom", new RuntimeException())).when(storageService).delete("users/x/assets/y/z.wav");

        assertThatThrownBy(() -> assetService.deleteFile(ownerId, assetId)).isInstanceOf(StorageException.class);

        assertThat(asset.getStorageKey()).isEqualTo("users/x/assets/y/z.wav"); // untouched
        verify(assetRepository, never()).save(any());
    }

    @Test
    void deleteFile_assetHasNoFile_throwsNotFound() {
        Asset asset = ownedAsset(assetId, ownerId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> assetService.deleteFile(ownerId, assetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
