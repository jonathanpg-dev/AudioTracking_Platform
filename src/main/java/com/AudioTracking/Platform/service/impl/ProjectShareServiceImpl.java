package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.project.CreateProjectShareRequest;
import com.AudioTracking.Platform.dto.project.ProjectShareResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectShareRequest;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectShare;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ProjectShareMapper;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.ProjectAccessService;
import com.AudioTracking.Platform.service.ProjectShareService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectShareServiceImpl implements ProjectShareService {

    private final ProjectShareRepository projectShareRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectShareMapper projectShareMapper;

    public ProjectShareServiceImpl(ProjectShareRepository projectShareRepository, UserRepository userRepository,
                                    ProjectAccessService projectAccessService, ProjectShareMapper projectShareMapper) {
        this.projectShareRepository = projectShareRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.projectShareMapper = projectShareMapper;
    }

    @Override
    public ProjectShareResponse createShare(UUID ownerId, UUID projectId, CreateProjectShareRequest request) {
        // Only the owner may share -- a 404 for an unrelated caller, 403 for a collaborator who
        // isn't the owner attempting to manage shares themselves.
        Project project = projectAccessService.requireOwnerAccess(ownerId, projectId);

        User target = userRepository.findByEmail(request.userEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email '" + request.userEmail() + "'"));

        if (projectShareRepository.existsByProjectIdAndUserId(projectId, target.getId())) {
            throw new DuplicateResourceException("This project is already shared with that user");
        }

        ProjectShare share = new ProjectShare();
        share.setProject(project);
        share.setUser(target);
        share.setPermission(request.permission());
        return projectShareMapper.toResponse(projectShareRepository.save(share));
    }

    @Override
    public List<ProjectShareResponse> getShares(UUID ownerId, UUID projectId) {
        projectAccessService.requireOwnerAccess(ownerId, projectId);
        return projectShareMapper.toResponseList(projectShareRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId));
    }

    @Override
    public ProjectShareResponse updateShare(UUID ownerId, UUID projectId, UUID shareId, UpdateProjectShareRequest request) {
        projectAccessService.requireOwnerAccess(ownerId, projectId);
        ProjectShare share = findShareOrThrow(projectId, shareId);
        share.setPermission(request.permission());
        return projectShareMapper.toResponse(projectShareRepository.save(share));
    }

    @Override
    public void deleteShare(UUID ownerId, UUID projectId, UUID shareId) {
        projectAccessService.requireOwnerAccess(ownerId, projectId);
        ProjectShare share = findShareOrThrow(projectId, shareId);
        // Only revokes this one collaborator's access -- the User, Project, its Assets, and
        // everything else are completely untouched.
        projectShareRepository.delete(share);
    }

    private ProjectShare findShareOrThrow(UUID projectId, UUID shareId) {
        return projectShareRepository.findByIdAndProjectId(shareId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Share with id '" + shareId + "' not found"));
    }
}
