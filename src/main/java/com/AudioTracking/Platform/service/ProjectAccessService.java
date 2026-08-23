package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.ProjectRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Single, central authority for "can this User do X to this Project" -- every other service
// (AssetServiceImpl, ProjectServiceImpl, ProjectShareServiceImpl) consults this instead of
// re-deriving owner/collaborator logic itself. See docs/collaboration.md for the full model.
//
// Each method returns the loaded Project on success, or throws:
//   - ResourceNotFoundException  if the user has NO relationship to the project at all (matches
//     every other owned-entity lookup in this app: unrelated users get a 404 that reveals nothing)
//   - InsufficientPermissionException  if the user IS related (owner or some share exists) but
//     doesn't have enough permission for the specific operation (a 403 -- they already know this
//     project exists, so hiding it behind a 404 wouldn't protect anything)
public interface ProjectAccessService {

    // Owner, or any collaborator (VIEW or EDIT).
    Project requireViewAccess(UUID userId, UUID projectId);

    // Owner, or an EDIT collaborator specifically.
    Project requireEditAccess(UUID userId, UUID projectId);

    // Owner only. Every share-management and Project-administrative operation goes through this.
    Project requireOwnerAccess(UUID userId, UUID projectId);

    // For when a caller needs to know WHICH relationship applies, not just whether one grants
    // enough access -- e.g. ProjectResponse.myRole, so the frontend can render permission-aware
    // UI without guessing. Takes an already-loaded Project (the caller already fetched one via
    // one of the requireXAccess methods above) to avoid a redundant lookup.
    ProjectRole getRole(UUID userId, Project project);

    // Batch counterpart to getRole -- resolves the caller's role for each of a list of Projects
    // they're already known to have SOME access to (e.g. from
    // ProjectRepository#findAllAccessibleByUserId). One query for all the caller's shares instead
    // of one getRole() round-trip per Project in the list.
    Map<UUID, ProjectRole> getRoles(UUID userId, List<Project> projects);
}
