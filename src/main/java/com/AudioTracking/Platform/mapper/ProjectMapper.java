package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectMapper {

    public Project toEntity(CreateProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(ProjectStatus.PLANNING);
        return project;
    }

    public void updateEntity(UpdateProjectRequest request, Project existing) {
        existing.setName(request.name());
        existing.setDescription(request.description());
        existing.setStatus(request.status());
    }

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public List<ProjectResponse> toResponseList(List<Project> projects) {
        return projects.stream().map(this::toResponse).toList();
    }
}
