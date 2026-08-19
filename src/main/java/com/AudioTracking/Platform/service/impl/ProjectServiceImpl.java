package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ProjectMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final ProjectMapper projectMapper;

    public ProjectServiceImpl(ProjectRepository projectRepository, UserRepository userRepository,
                               AssetRepository assetRepository, ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.projectMapper = projectMapper;
    }

    @Override
    public ProjectResponse createProject(UUID ownerId, CreateProjectRequest request) {
        Project project = projectMapper.toEntity(request);
        project.setUser(userRepository.getReferenceById(ownerId));
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Override
    public List<ProjectResponse> getProjects(UUID ownerId) {
        return projectMapper.toResponseList(projectRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId));
    }

    @Override
    public ProjectResponse getProject(UUID ownerId, UUID projectId) {
        return projectMapper.toResponse(findOwnedOrThrow(ownerId, projectId));
    }

    @Override
    public ProjectResponse updateProject(UUID ownerId, UUID projectId, UpdateProjectRequest request) {
        Project existing = findOwnedOrThrow(ownerId, projectId);
        projectMapper.updateEntity(request, existing);
        return projectMapper.toResponse(projectRepository.save(existing));
    }

    @Override
    @Transactional // unassigning every affected asset + the delete itself must succeed together
    public void deleteProject(UUID ownerId, UUID projectId) {
        Project existing = findOwnedOrThrow(ownerId, projectId);
        // Assets must survive their project being deleted — just become unassigned, not removed.
        // findAllByProjectId is a plain query (not a bulk update), so the returned Asset
        // instances are normal managed entities: setting a field on them here is enough, no
        // explicit save() needed, and — unlike a native/bulk query — nothing goes stale in the
        // persistence context for anything already loaded elsewhere in this transaction.
        for (Asset asset : assetRepository.findAllByProjectId(projectId)) {
            asset.setProject(null);
        }
        projectRepository.delete(existing);
    }

    private Project findOwnedOrThrow(UUID ownerId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project with id '" + projectId + "' not found"));
    }
}
