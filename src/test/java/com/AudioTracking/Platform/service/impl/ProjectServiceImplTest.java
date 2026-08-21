package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Client;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectStatus;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.InsufficientPermissionException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ProjectMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private AnalyticsService analyticsService;
    @Mock private ProjectMapper projectMapper;

    private ProjectServiceImpl projectService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectRepository, userRepository, assetRepository,
                clientRepository, projectAccessService, analyticsService, projectMapper);
    }

    @Test
    void createProject_assignsAuthenticatedUserAsOwner() {
        CreateProjectRequest request = new CreateProjectRequest("R&B EP", null, null);
        Project mapped = new Project();
        when(projectMapper.toEntity(request)).thenReturn(mapped);

        User ownerRef = new User();
        ownerRef.setId(ownerId);
        when(userRepository.getReferenceById(ownerId)).thenReturn(ownerRef);

        Project saved = new Project();
        saved.setId(projectId);
        when(projectRepository.save(mapped)).thenReturn(saved);
        ProjectResponse expected = mock(ProjectResponse.class);
        when(projectMapper.toResponse(saved)).thenReturn(expected);

        ProjectResponse result = projectService.createProject(ownerId, request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(ownerRef);
    }

    @Test
    void createProject_withOwnedClientId_assignsClient() {
        UUID clientId = UUID.randomUUID();
        CreateProjectRequest request = new CreateProjectRequest("R&B EP", null, clientId);
        Project mapped = new Project();
        when(projectMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());

        Client client = new Client();
        client.setId(clientId);
        when(clientRepository.findByIdAndUserId(clientId, ownerId)).thenReturn(Optional.of(client));
        when(projectRepository.save(mapped)).thenReturn(mapped);
        when(projectMapper.toResponse(mapped)).thenReturn(mock(ProjectResponse.class));

        projectService.createProject(ownerId, request);

        assertThat(mapped.getClient()).isSameAs(client);
    }

    @Test
    void createProject_withAnotherUsersClientId_throwsNotFound() {
        UUID clientId = UUID.randomUUID();
        CreateProjectRequest request = new CreateProjectRequest("R&B EP", null, clientId);
        when(projectMapper.toEntity(request)).thenReturn(new Project());
        when(userRepository.getReferenceById(ownerId)).thenReturn(new User());
        when(clientRepository.findByIdAndUserId(clientId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.createProject(ownerId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void getProject_delegatesToProjectAccessService_forViewAccess() {
        Project project = new Project();
        project.setId(projectId);
        when(projectAccessService.requireViewAccess(ownerId, projectId)).thenReturn(project);
        ProjectResponse expected = mock(ProjectResponse.class);
        when(projectMapper.toResponse(project)).thenReturn(expected);

        ProjectResponse result = projectService.getProject(ownerId, projectId);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getProject_unrelatedCaller_propagatesNotFound() {
        when(projectAccessService.requireViewAccess(otherUserId, projectId))
                .thenThrow(new ResourceNotFoundException("Project with id '" + projectId + "' not found"));

        assertThatThrownBy(() -> projectService.getProject(otherUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProject_requiresOwnerAccess_appliesMapperAndSaves() {
        UpdateProjectRequest request = new UpdateProjectRequest("Renamed", "New description", ProjectStatus.IN_PROGRESS, null);
        Project existing = new Project();
        existing.setId(projectId);
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(existing);
        when(projectRepository.save(existing)).thenReturn(existing);
        ProjectResponse expected = mock(ProjectResponse.class);
        when(projectMapper.toResponse(existing)).thenReturn(expected);

        ProjectResponse result = projectService.updateProject(ownerId, projectId, request);

        assertThat(result).isSameAs(expected);
        verify(projectMapper).updateEntity(request, existing);
    }

    @Test
    void updateProject_editCollaborator_isRejected_administrativeOperation() {
        // EDIT collaborators can touch resources/assets, never the Project's own administrative
        // fields (name/description/status/client) -- see docs/collaboration.md.
        UpdateProjectRequest request = new UpdateProjectRequest("Renamed", null, ProjectStatus.COMPLETED, null);
        when(projectAccessService.requireOwnerAccess(otherUserId, projectId))
                .thenThrow(new InsufficientPermissionException("Only the project owner can perform this action"));

        assertThatThrownBy(() -> projectService.updateProject(otherUserId, projectId, request))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(projectMapper, never()).updateEntity(any(), any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_unrelatedCaller_throwsNotFound_andNeverWrites() {
        UpdateProjectRequest request = new UpdateProjectRequest("Renamed", null, ProjectStatus.COMPLETED, null);
        when(projectAccessService.requireOwnerAccess(otherUserId, projectId))
                .thenThrow(new ResourceNotFoundException("Project with id '" + projectId + "' not found"));

        assertThatThrownBy(() -> projectService.updateProject(otherUserId, projectId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectMapper, never()).updateEntity(any(), any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void deleteProject_notOwnedByCaller_throwsNotFound_andNeverDeletes() {
        when(projectAccessService.requireOwnerAccess(otherUserId, projectId))
                .thenThrow(new ResourceNotFoundException("Project with id '" + projectId + "' not found"));

        assertThatThrownBy(() -> projectService.deleteProject(otherUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).delete(any());
    }

    @Test
    void deleteProject_editCollaborator_isRejected() {
        doThrow(new InsufficientPermissionException("Only the project owner can perform this action"))
                .when(projectAccessService).requireOwnerAccess(otherUserId, projectId);

        assertThatThrownBy(() -> projectService.deleteProject(otherUserId, projectId))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(projectRepository, never()).delete(any());
    }

    @Test
    void deleteProject_ownedByCaller_deletesIt() {
        Project existing = new Project();
        existing.setId(projectId);
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(existing);

        projectService.deleteProject(ownerId, projectId);

        verify(projectRepository).delete(existing);
    }

    @Test
    void deleteProject_unassignsEveryAssetThatReferencedIt_withoutDeletingThem() {
        Project existing = new Project();
        existing.setId(projectId);
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(existing);

        Asset assetA = new Asset();
        assetA.setProject(existing);
        Asset assetB = new Asset();
        assetB.setProject(existing);
        when(assetRepository.findAllByProjectId(projectId)).thenReturn(List.of(assetA, assetB));

        projectService.deleteProject(ownerId, projectId);

        assertThat(assetA.getProject()).isNull();
        assertThat(assetB.getProject()).isNull();
        verify(assetRepository, never()).delete(any());
        verify(projectRepository).delete(existing);
    }
}
