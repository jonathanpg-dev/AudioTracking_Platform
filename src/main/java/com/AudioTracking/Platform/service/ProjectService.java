package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    ProjectResponse createProject(UUID ownerId, CreateProjectRequest request);

    List<ProjectResponse> getProjects(UUID ownerId, Sort sort);

    // Projects where the caller is the assigned Client (myRole CLIENT on every row) -- a
    // deliberately separate list from getProjects above. See docs/collaboration.md.
    List<ProjectResponse> getProjectsAsClient(UUID userId, Sort sort);

    ProjectResponse getProject(UUID ownerId, UUID projectId);

    ProjectResponse updateProject(UUID ownerId, UUID projectId, UpdateProjectRequest request);

    void deleteProject(UUID ownerId, UUID projectId);
}
