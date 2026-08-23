package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectPermission;
import com.AudioTracking.Platform.entity.ProjectRole;
import com.AudioTracking.Platform.entity.ProjectShare;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.InsufficientPermissionException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// The single central authority every other Phase 5 authorization check delegates to -- covered
// directly and exhaustively here, on top of the behavior it produces being proven again at the
// HTTP layer in ProjectCollaborationIntegrationTest/ProjectShareControllerIntegrationTest.
@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectShareRepository projectShareRepository;

    private ProjectAccessServiceImpl accessService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID viewCollaboratorId = UUID.randomUUID();
    private final UUID editCollaboratorId = UUID.randomUUID();
    private final UUID unrelatedUserId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private Project project;

    @BeforeEach
    void setUp() {
        accessService = new ProjectAccessServiceImpl(projectRepository, projectShareRepository);

        User owner = new User();
        owner.setId(ownerId);
        project = new Project();
        project.setId(projectId);
        project.setUser(owner);
    }

    private ProjectShare shareWith(UUID userId, ProjectPermission permission) {
        User user = new User();
        user.setId(userId);
        ProjectShare share = new ProjectShare();
        share.setProject(project);
        share.setUser(user);
        share.setPermission(permission);
        return share;
    }

    @Test
    void requireViewAccess_nonexistentProject_throwsNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireViewAccess(ownerId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireViewAccess_owner_succeeds() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(accessService.requireViewAccess(ownerId, projectId)).isSameAs(project);
    }

    @Test
    void requireViewAccess_viewCollaborator_succeeds() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.findByProjectIdAndUserId(projectId, viewCollaboratorId))
                .thenReturn(Optional.of(shareWith(viewCollaboratorId, ProjectPermission.VIEW)));

        assertThat(accessService.requireViewAccess(viewCollaboratorId, projectId)).isSameAs(project);
    }

    @Test
    void requireViewAccess_editCollaborator_alsoSucceeds() {
        // EDIT implies at least VIEW.
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.findByProjectIdAndUserId(projectId, editCollaboratorId))
                .thenReturn(Optional.of(shareWith(editCollaboratorId, ProjectPermission.EDIT)));

        assertThat(accessService.requireViewAccess(editCollaboratorId, projectId)).isSameAs(project);
    }

    @Test
    void requireViewAccess_unrelatedUser_throwsNotFound_notForbidden() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.findByProjectIdAndUserId(projectId, unrelatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireViewAccess(unrelatedUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireEditAccess_owner_succeeds() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(accessService.requireEditAccess(ownerId, projectId)).isSameAs(project);
    }

    @Test
    void requireEditAccess_editCollaborator_succeeds() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.findByProjectIdAndUserId(projectId, editCollaboratorId))
                .thenReturn(Optional.of(shareWith(editCollaboratorId, ProjectPermission.EDIT)));

        assertThat(accessService.requireEditAccess(editCollaboratorId, projectId)).isSameAs(project);
    }

    @Test
    void requireEditAccess_viewCollaborator_throwsInsufficientPermission_notNotFound() {
        // The key distinction from an unrelated user: they DO have a relationship to this
        // project (they can view it), so a 403 is correct -- a 404 would be misleading.
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.findByProjectIdAndUserId(projectId, viewCollaboratorId))
                .thenReturn(Optional.of(shareWith(viewCollaboratorId, ProjectPermission.VIEW)));

        assertThatThrownBy(() -> accessService.requireEditAccess(viewCollaboratorId, projectId))
                .isInstanceOf(InsufficientPermissionException.class);
    }

    @Test
    void requireEditAccess_unrelatedUser_throwsNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.findByProjectIdAndUserId(projectId, unrelatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireEditAccess(unrelatedUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireOwnerAccess_owner_succeeds() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(accessService.requireOwnerAccess(ownerId, projectId)).isSameAs(project);
    }

    @Test
    void requireOwnerAccess_editCollaborator_throwsInsufficientPermission_notNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.existsByProjectIdAndUserId(projectId, editCollaboratorId)).thenReturn(true);

        assertThatThrownBy(() -> accessService.requireOwnerAccess(editCollaboratorId, projectId))
                .isInstanceOf(InsufficientPermissionException.class);
    }

    @Test
    void requireOwnerAccess_viewCollaborator_throwsInsufficientPermission_notNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.existsByProjectIdAndUserId(projectId, viewCollaboratorId)).thenReturn(true);

        assertThatThrownBy(() -> accessService.requireOwnerAccess(viewCollaboratorId, projectId))
                .isInstanceOf(InsufficientPermissionException.class);
    }

    @Test
    void requireOwnerAccess_unrelatedUser_throwsNotFound_neverRevealingItExists() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectShareRepository.existsByProjectIdAndUserId(projectId, unrelatedUserId)).thenReturn(false);

        assertThatThrownBy(() -> accessService.requireOwnerAccess(unrelatedUserId, projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getRole (Phase 7: the backend-computed signal behind ProjectResponse.myRole) ---

    @Test
    void getRole_owner_returnsOwner() {
        assertThat(accessService.getRole(ownerId, project)).isEqualTo(com.AudioTracking.Platform.entity.ProjectRole.OWNER);
    }

    @Test
    void getRole_viewCollaborator_returnsView() {
        when(projectShareRepository.findByProjectIdAndUserId(projectId, viewCollaboratorId))
                .thenReturn(Optional.of(shareWith(viewCollaboratorId, ProjectPermission.VIEW)));

        assertThat(accessService.getRole(viewCollaboratorId, project)).isEqualTo(com.AudioTracking.Platform.entity.ProjectRole.VIEW);
    }

    @Test
    void getRole_editCollaborator_returnsEdit() {
        when(projectShareRepository.findByProjectIdAndUserId(projectId, editCollaboratorId))
                .thenReturn(Optional.of(shareWith(editCollaboratorId, ProjectPermission.EDIT)));

        assertThat(accessService.getRole(editCollaboratorId, project)).isEqualTo(com.AudioTracking.Platform.entity.ProjectRole.EDIT);
    }

    @Test
    void getRole_unrelatedUser_throwsNotFound() {
        when(projectShareRepository.findByProjectIdAndUserId(projectId, unrelatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.getRole(unrelatedUserId, project))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getRoles (batch counterpart, backing GET /projects now that it includes shared Projects) ---

    @Test
    void getRoles_ownedProject_returnsOwner() {
        Map<UUID, ProjectRole> roles = accessService.getRoles(ownerId, List.of(project));

        assertThat(roles).containsEntry(projectId, ProjectRole.OWNER);
    }

    @Test
    void getRoles_viewCollaborator_returnsView() {
        when(projectShareRepository.findAllByUserId(viewCollaboratorId))
                .thenReturn(List.of(shareWith(viewCollaboratorId, ProjectPermission.VIEW)));

        Map<UUID, ProjectRole> roles = accessService.getRoles(viewCollaboratorId, List.of(project));

        assertThat(roles).containsEntry(projectId, ProjectRole.VIEW);
    }

    @Test
    void getRoles_editCollaborator_returnsEdit() {
        when(projectShareRepository.findAllByUserId(editCollaboratorId))
                .thenReturn(List.of(shareWith(editCollaboratorId, ProjectPermission.EDIT)));

        Map<UUID, ProjectRole> roles = accessService.getRoles(editCollaboratorId, List.of(project));

        assertThat(roles).containsEntry(projectId, ProjectRole.EDIT);
    }

    @Test
    void getRoles_resolvesEachProjectIndependently_notJustTheFirstOne() {
        // Owns `project`; is only an EDIT collaborator on a second, unrelated Project -- proves
        // the batch resolution doesn't just apply one role to everything in the list.
        Project otherProject = new Project();
        UUID otherProjectId = UUID.randomUUID();
        otherProject.setId(otherProjectId);
        User otherOwner = new User();
        otherOwner.setId(UUID.randomUUID());
        otherProject.setUser(otherOwner);

        ProjectShare shareOnOther = new ProjectShare();
        shareOnOther.setProject(otherProject);
        User self = new User();
        self.setId(ownerId);
        shareOnOther.setUser(self);
        shareOnOther.setPermission(ProjectPermission.EDIT);
        when(projectShareRepository.findAllByUserId(ownerId)).thenReturn(List.of(shareOnOther));

        Map<UUID, ProjectRole> roles = accessService.getRoles(ownerId, List.of(project, otherProject));

        assertThat(roles).containsEntry(projectId, ProjectRole.OWNER);
        assertThat(roles).containsEntry(otherProjectId, ProjectRole.EDIT);
    }

    @Test
    void getRoles_emptyProjectList_returnsEmptyMap_stillQueriesSharesOnce() {
        when(projectShareRepository.findAllByUserId(ownerId)).thenReturn(List.of());

        assertThat(accessService.getRoles(ownerId, List.of())).isEmpty();
    }
}
