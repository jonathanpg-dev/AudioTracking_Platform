package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.project.CreateProjectShareRequest;
import com.AudioTracking.Platform.dto.project.ProjectShareResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectShareRequest;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectPermission;
import com.AudioTracking.Platform.entity.ProjectShare;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.InsufficientPermissionException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ProjectShareMapper;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.ProjectAccessService;
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
class ProjectShareServiceImplTest {

    @Mock private ProjectShareRepository projectShareRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private AnalyticsService analyticsService;
    @Mock private ProjectShareMapper projectShareMapper;

    private ProjectShareServiceImpl shareService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID collaboratorId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        shareService = new ProjectShareServiceImpl(projectShareRepository, userRepository, projectAccessService, analyticsService, projectShareMapper);
    }

    @Test
    void createShare_requiresOwnerAccess_beforeDoingAnythingElse() {
        CreateProjectShareRequest request = new CreateProjectShareRequest("collab@example.com", ProjectPermission.VIEW);
        when(projectAccessService.requireOwnerAccess(collaboratorId, projectId))
                .thenThrow(new InsufficientPermissionException("Only the project owner can perform this action"));

        assertThatThrownBy(() -> shareService.createShare(collaboratorId, projectId, request))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(projectShareRepository, never()).save(any());
    }

    @Test
    void createShare_targetEmailNotRegistered_throwsNotFound_createsNoUser() {
        CreateProjectShareRequest request = new CreateProjectShareRequest("nobody@example.com", ProjectPermission.VIEW);
        Project project = new Project();
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(project);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.createShare(ownerId, projectId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectShareRepository, never()).save(any());
    }

    @Test
    void createShare_alreadyShared_throwsDuplicate_doesNotCreateSecondShare() {
        CreateProjectShareRequest request = new CreateProjectShareRequest("collab@example.com", ProjectPermission.EDIT);
        Project project = new Project();
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(project);
        User target = new User();
        target.setId(collaboratorId);
        when(userRepository.findByEmail("collab@example.com")).thenReturn(Optional.of(target));
        when(projectShareRepository.existsByProjectIdAndUserId(projectId, collaboratorId)).thenReturn(true);

        assertThatThrownBy(() -> shareService.createShare(ownerId, projectId, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(projectShareRepository, never()).save(any());
    }

    @Test
    void createShare_newTarget_savesShareWithRequestedPermission() {
        CreateProjectShareRequest request = new CreateProjectShareRequest("collab@example.com", ProjectPermission.EDIT);
        Project project = new Project();
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(project);
        User target = new User();
        target.setId(collaboratorId);
        when(userRepository.findByEmail("collab@example.com")).thenReturn(Optional.of(target));
        when(projectShareRepository.existsByProjectIdAndUserId(projectId, collaboratorId)).thenReturn(false);
        when(projectShareRepository.save(any(ProjectShare.class))).thenAnswer(inv -> inv.getArgument(0));
        ProjectShareResponse expected = mock(ProjectShareResponse.class);
        when(projectShareMapper.toResponse(any(ProjectShare.class))).thenReturn(expected);

        ProjectShareResponse result = shareService.createShare(ownerId, projectId, request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<ProjectShare> captor = ArgumentCaptor.forClass(ProjectShare.class);
        verify(projectShareRepository).save(captor.capture());
        assertThat(captor.getValue().getProject()).isSameAs(project);
        assertThat(captor.getValue().getUser()).isSameAs(target);
        assertThat(captor.getValue().getPermission()).isEqualTo(ProjectPermission.EDIT);
    }

    @Test
    void getShares_requiresOwnerAccess() {
        when(projectAccessService.requireOwnerAccess(collaboratorId, projectId))
                .thenThrow(new InsufficientPermissionException("Only the project owner can perform this action"));

        assertThatThrownBy(() -> shareService.getShares(collaboratorId, projectId))
                .isInstanceOf(InsufficientPermissionException.class);
    }

    @Test
    void updateShare_shareBelongsToDifferentProject_throwsNotFound() {
        UpdateProjectShareRequest request = new UpdateProjectShareRequest(ProjectPermission.EDIT);
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(new Project());
        when(projectShareRepository.findByIdAndProjectId(shareId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.updateShare(ownerId, projectId, shareId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateShare_ownedByCaller_changesPermission() {
        UpdateProjectShareRequest request = new UpdateProjectShareRequest(ProjectPermission.EDIT);
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(new Project());
        ProjectShare share = new ProjectShare();
        share.setPermission(ProjectPermission.VIEW);
        when(projectShareRepository.findByIdAndProjectId(shareId, projectId)).thenReturn(Optional.of(share));
        when(projectShareRepository.save(share)).thenReturn(share);
        when(projectShareMapper.toResponse(share)).thenReturn(mock(ProjectShareResponse.class));

        shareService.updateShare(ownerId, projectId, shareId, request);

        assertThat(share.getPermission()).isEqualTo(ProjectPermission.EDIT);
    }

    @Test
    void deleteShare_requiresOwnerAccess_thenDeletesOnlyThatShare() {
        when(projectAccessService.requireOwnerAccess(ownerId, projectId)).thenReturn(new Project());
        ProjectShare share = new ProjectShare();
        when(projectShareRepository.findByIdAndProjectId(shareId, projectId)).thenReturn(Optional.of(share));

        shareService.deleteShare(ownerId, projectId, shareId);

        verify(projectShareRepository).delete(share);
    }
}
