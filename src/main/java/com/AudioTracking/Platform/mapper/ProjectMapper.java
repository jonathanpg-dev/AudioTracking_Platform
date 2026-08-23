package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.entity.Client;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectRole;
import com.AudioTracking.Platform.entity.ProjectStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ProjectMapper {

    public Project toEntity(CreateProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(ProjectStatus.PLANNING);
        return project;
    }

    // Deliberately does NOT touch client here — clientId resolution needs a repository lookup
    // (ownership check), so ProjectServiceImpl sets it separately after calling this, same as
    // AssetMapper/AssetServiceImpl handle Asset.project.
    public void updateEntity(UpdateProjectRequest request, Project existing) {
        existing.setName(request.name());
        existing.setDescription(request.description());
        existing.setStatus(request.status());
    }

    public ProjectResponse toResponse(Project project, ProjectRole myRole) {
        Client client = project.getClient();
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                client == null ? null : client.getId(),
                client == null ? null : client.getName(),
                myRole);
    }

    // GET /projects now includes both owned and shared-with-me Projects (see
    // docs/collaboration.md and ProjectServiceImpl#getProjects), so the caller's role can no
    // longer be assumed to be OWNER for every row -- rolesByProjectId comes from
    // ProjectAccessService#getRoles, computed once for the whole list.
    public List<ProjectResponse> toResponseList(List<Project> projects, Map<UUID, ProjectRole> rolesByProjectId) {
        return projects.stream().map(project -> toResponse(project, rolesByProjectId.get(project.getId()))).toList();
    }
}
