package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    ProjectResponse createProject(UUID ownerId, CreateProjectRequest request);

    List<ProjectResponse> getProjects(UUID ownerId);

    ProjectResponse getProject(UUID ownerId, UUID projectId);

    ProjectResponse updateProject(UUID ownerId, UUID projectId, UpdateProjectRequest request);

    void deleteProject(UUID ownerId, UUID projectId);
}
