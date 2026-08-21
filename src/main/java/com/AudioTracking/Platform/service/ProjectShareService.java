package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.project.CreateProjectShareRequest;
import com.AudioTracking.Platform.dto.project.ProjectShareResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectShareRequest;

import java.util.List;
import java.util.UUID;

// Every method here is owner-only -- see ProjectAccessService#requireOwnerAccess. A collaborator
// (VIEW or EDIT) can never create, list, update, or remove shares, even on a project they
// themselves collaborate on. See docs/collaboration.md.
public interface ProjectShareService {

    ProjectShareResponse createShare(UUID ownerId, UUID projectId, CreateProjectShareRequest request);

    List<ProjectShareResponse> getShares(UUID ownerId, UUID projectId);

    ProjectShareResponse updateShare(UUID ownerId, UUID projectId, UUID shareId, UpdateProjectShareRequest request);

    void deleteShare(UUID ownerId, UUID projectId, UUID shareId);
}
