package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectStatus;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ProjectMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.UserRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ProjectMapper projectMapper;

    private ProjectServiceImpl projectService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectRepository, userRepository, assetRepository, projectMapper);
    }

    @Test
    void createProject_assignsAuthenticatedUserAsOwner() {
        CreateProjectRequest request = new CreateProjectRequest("R&B EP", null);
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
    void getProject_notOwnedByCaller_throwsNotFound_sameAsNonexistentId() {
        when(projectRepository.findByIdAndUserId(projectId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(otherUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProject_ownedByCaller_appliesMapperAndSaves() {
        UpdateProjectRequest request = new UpdateProjectRequest("Renamed", "New description", ProjectStatus.IN_PROGRESS);
        Project existing = new Project();
        existing.setId(projectId);
        when(projectRepository.findByIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(existing));
        when(projectRepository.save(existing)).thenReturn(existing);
        ProjectResponse expected = mock(ProjectResponse.class);
        when(projectMapper.toResponse(existing)).thenReturn(expected);

        ProjectResponse result = projectService.updateProject(ownerId, projectId, request);

        assertThat(result).isSameAs(expected);
        verify(projectMapper).updateEntity(request, existing);
    }

    @Test
    void updateProject_notOwnedByCaller_throwsNotFound_andNeverWrites() {
        UpdateProjectRequest request = new UpdateProjectRequest("Renamed", null, ProjectStatus.COMPLETED);
        when(projectRepository.findByIdAndUserId(projectId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateProject(otherUserId, projectId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectMapper, never()).updateEntity(any(), any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void deleteProject_notOwnedByCaller_throwsNotFound_andNeverDeletes() {
        when(projectRepository.findByIdAndUserId(projectId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.deleteProject(otherUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).delete(any());
    }

    @Test
    void deleteProject_ownedByCaller_deletesIt() {
        Project existing = new Project();
        existing.setId(projectId);
        when(projectRepository.findByIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(existing));

        projectService.deleteProject(ownerId, projectId);

        verify(projectRepository).delete(existing);
    }

    @Test
    void deleteProject_unassignsEveryAssetThatReferencedIt_withoutDeletingThem() {
        Project existing = new Project();
        existing.setId(projectId);
        when(projectRepository.findByIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(existing));

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
