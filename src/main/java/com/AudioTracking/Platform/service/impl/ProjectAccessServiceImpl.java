package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectPermission;
import com.AudioTracking.Platform.entity.ProjectRole;
import com.AudioTracking.Platform.entity.ProjectShare;
import com.AudioTracking.Platform.exception.InsufficientPermissionException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import com.AudioTracking.Platform.service.ProjectAccessService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectAccessServiceImpl implements ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectShareRepository projectShareRepository;

    public ProjectAccessServiceImpl(ProjectRepository projectRepository, ProjectShareRepository projectShareRepository) {
        this.projectRepository = projectRepository;
        this.projectShareRepository = projectShareRepository;
    }

    @Override
    public Project requireViewAccess(UUID userId, UUID projectId) {
        Project project = loadOrThrow(projectId);
        if (isOwner(project, userId) || isLinkedClient(project, userId)) {
            return project;
        }
        // Any share at all -- VIEW or EDIT -- grants view access.
        findShareOrThrowNotFound(projectId, userId);
        return project;
    }

    @Override
    public Project requireEditAccess(UUID userId, UUID projectId) {
        Project project = loadOrThrow(projectId);
        if (isOwner(project, userId)) {
            return project;
        }
        if (isLinkedClient(project, userId)) {
            // They know this Project exists (they can view it) -- 403, not a 404 that pretends
            // otherwise. A client's access never extends past view + their own client notes.
            throw new InsufficientPermissionException("Clients cannot perform this action");
        }
        ProjectShare share = findShareOrThrowNotFound(projectId, userId);
        if (share.getPermission() != ProjectPermission.EDIT) {
            throw new InsufficientPermissionException("VIEW collaborators cannot perform this action");
        }
        return project;
    }

    @Override
    public Project requireOwnerAccess(UUID userId, UUID projectId) {
        Project project = loadOrThrow(projectId);
        if (isOwner(project, userId)) {
            return project;
        }
        // A collaborator (VIEW or EDIT) or the assigned client already knows this project exists
        // -> 403, not a 404 that pretends otherwise. Someone with no relationship at all still
        // gets a 404 below.
        if (isLinkedClient(project, userId) || projectShareRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new InsufficientPermissionException("Only the project owner can perform this action");
        }
        throw new ResourceNotFoundException("Project with id '" + projectId + "' not found");
    }

    @Override
    public ProjectRole getRole(UUID userId, Project project) {
        if (isOwner(project, userId)) {
            return ProjectRole.OWNER;
        }
        if (isLinkedClient(project, userId)) {
            return ProjectRole.CLIENT;
        }
        ProjectShare share = findShareOrThrowNotFound(project.getId(), userId);
        return share.getPermission() == ProjectPermission.EDIT ? ProjectRole.EDIT : ProjectRole.VIEW;
    }

    @Override
    public Map<UUID, ProjectRole> getRoles(UUID userId, List<Project> projects) {
        // One query for every share this user holds, anywhere -- looked up once regardless of how
        // many Projects are being resolved, then matched in memory rather than re-querying per
        // Project the way a loop of getRole() calls would.
        Map<UUID, ProjectPermission> permissionByProjectId = projectShareRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(share -> share.getProject().getId(), ProjectShare::getPermission));

        Map<UUID, ProjectRole> roles = new HashMap<>();
        for (Project project : projects) {
            if (isOwner(project, userId)) {
                roles.put(project.getId(), ProjectRole.OWNER);
                continue;
            }
            // Client-access Projects never appear in the list this backs (findAllAccessibleByUserId
            // only looks at ownership/shares) -- isLinkedClient is never true here in practice, but
            // checking costs nothing and keeps this in sync with getRole's precedence if that ever
            // changes.
            if (isLinkedClient(project, userId)) {
                roles.put(project.getId(), ProjectRole.CLIENT);
                continue;
            }
            ProjectPermission permission = permissionByProjectId.get(project.getId());
            roles.put(project.getId(), permission == ProjectPermission.EDIT ? ProjectRole.EDIT : ProjectRole.VIEW);
        }
        return roles;
    }

    private Project loadOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project with id '" + projectId + "' not found"));
    }

    private ProjectShare findShareOrThrowNotFound(UUID projectId, UUID userId) {
        return projectShareRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project with id '" + projectId + "' not found"));
    }

    private boolean isOwner(Project project, UUID userId) {
        return project.getUser().getId().equals(userId);
    }

    // True when this Project has an assigned Client whose login account (Client.linkedUser) is
    // this User. See Client.linkedUser and ClientServiceImpl#resolveLinkedUserOrNull for how that
    // link is established, and ProjectRole.CLIENT for what it grants.
    private boolean isLinkedClient(Project project, UUID userId) {
        return project.getClient() != null
                && project.getClient().getLinkedUser() != null
                && project.getClient().getLinkedUser().getId().equals(userId);
    }
}
