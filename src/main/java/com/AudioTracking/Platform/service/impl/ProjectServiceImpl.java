package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Client;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectRole;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ProjectMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.ProjectAccessService;
import com.AudioTracking.Platform.service.ProjectService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final ClientRepository clientRepository;
    private final ProjectAccessService projectAccessService;
    private final AnalyticsService analyticsService;
    private final ProjectMapper projectMapper;

    public ProjectServiceImpl(ProjectRepository projectRepository, UserRepository userRepository,
                               AssetRepository assetRepository, ClientRepository clientRepository,
                               ProjectAccessService projectAccessService, AnalyticsService analyticsService,
                               ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.clientRepository = clientRepository;
        this.projectAccessService = projectAccessService;
        this.analyticsService = analyticsService;
        this.projectMapper = projectMapper;
    }

    @Override
    public ProjectResponse createProject(UUID ownerId, CreateProjectRequest request) {
        Project project = projectMapper.toEntity(request);
        project.setUser(userRepository.getReferenceById(ownerId));
        project.setClient(resolveOwnedClientOrNull(ownerId, request.clientId()));
        Project saved = projectRepository.save(project);
        analyticsService.record(ownerId, AnalyticsEventType.PROJECT_CREATED, null, saved.getId());
        return projectMapper.toResponse(saved, ProjectRole.OWNER);
    }

    @Override
    public List<ProjectResponse> getProjects(UUID userId, Sort sort) {
        // Owned Projects AND Projects shared with this user, merged -- see
        // docs/collaboration.md. myRole is resolved per-Project (not assumed OWNER) since a
        // collaborator's own shared Projects now appear here too.
        List<Project> projects = projectRepository.findAllAccessibleByUserId(userId, sort);
        Map<UUID, ProjectRole> roles = projectAccessService.getRoles(userId, projects);
        return projectMapper.toResponseList(projects, roles);
    }

    @Override
    public List<ProjectResponse> getProjectsAsClient(UUID userId, Sort sort) {
        // Every row here has an assigned Client whose linkedUser is this caller -- myRole CLIENT
        // for all of them, no per-project role lookup needed (contrast with getProjects above).
        List<Project> projects = projectRepository.findAllByClientLinkedUserId(userId, sort);
        return projects.stream().map(project -> projectMapper.toResponse(project, ProjectRole.CLIENT)).toList();
    }

    @Override
    public ProjectResponse getProject(UUID requesterId, UUID projectId) {
        // Owner, VIEW collaborator, or EDIT collaborator can all view the project.
        Project project = projectAccessService.requireViewAccess(requesterId, projectId);
        ProjectRole role = projectAccessService.getRole(requesterId, project);
        return projectMapper.toResponse(project, role);
    }

    @Override
    public ProjectResponse updateProject(UUID ownerId, UUID projectId, UpdateProjectRequest request) {
        // Owner-only: name/description/status/client are administrative Project information, not
        // "creative resource collaboration" -- EDIT collaborators don't get this. See
        // docs/collaboration.md for the full reasoning.
        Project existing = projectAccessService.requireOwnerAccess(ownerId, projectId);
        projectMapper.updateEntity(request, existing);
        existing.setClient(resolveOwnedClientOrNull(ownerId, request.clientId()));
        Project saved = projectRepository.save(existing);
        analyticsService.record(ownerId, AnalyticsEventType.PROJECT_UPDATED, null, saved.getId());
        return projectMapper.toResponse(saved, ProjectRole.OWNER);
    }

    @Override
    @Transactional // unassigning every affected asset + the delete itself must succeed together
    public void deleteProject(UUID ownerId, UUID projectId) {
        // Owner-only: neither VIEW nor EDIT collaborators may ever delete the Project.
        Project existing = projectAccessService.requireOwnerAccess(ownerId, projectId);
        // Assets must survive their project being deleted — just become unassigned, not removed.
        // findAllByProjectId is a plain query (not a bulk update), so the returned Asset
        // instances are normal managed entities: setting a field on them here is enough, no
        // explicit save() needed, and — unlike a native/bulk query — nothing goes stale in the
        // persistence context for anything already loaded elsewhere in this transaction.
        for (Asset asset : assetRepository.findAllByProjectId(projectId)) {
            asset.setProject(null);
        }
        // ProjectShare rows are removed automatically via Project.shares' cascade — no manual
        // cleanup needed here. Client, Users, Tags, Collections are all untouched by this delete.
        projectRepository.delete(existing);
    }

    // null clientId means "no client" — not an error. A non-null id that isn't owned by this user
    // is treated the same as a nonexistent one: a user must never be able to associate their
    // project with someone else's client, and this must not reveal whether that client exists.
    private Client resolveOwnedClientOrNull(UUID ownerId, UUID clientId) {
        if (clientId == null) {
            return null;
        }
        return clientRepository.findByIdAndUserId(clientId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Client with id '" + clientId + "' not found"));
    }
}
