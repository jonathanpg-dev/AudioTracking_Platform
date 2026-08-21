package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectPermission;
import com.AudioTracking.Platform.entity.ProjectShare;
import com.AudioTracking.Platform.exception.InsufficientPermissionException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import com.AudioTracking.Platform.service.ProjectAccessService;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
        if (isOwner(project, userId)) {
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
        // A collaborator (VIEW or EDIT) already knows this project exists -> 403, not a 404 that
        // pretends otherwise. Someone with no relationship at all still gets a 404 below.
        if (projectShareRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new InsufficientPermissionException("Only the project owner can perform this action");
        }
        throw new ResourceNotFoundException("Project with id '" + projectId + "' not found");
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
}
