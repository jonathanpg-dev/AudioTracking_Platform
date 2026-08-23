package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.asset.AssetResponse;
import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.AssetService;
import com.AudioTracking.Platform.service.ProjectService;
import com.AudioTracking.Platform.util.SortParams;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final AssetService assetService;

    public ProjectController(ProjectService projectService, AssetService assetService) {
        this.projectService = projectService;
        this.assetService = assetService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                           @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                               @RequestParam(required = false) String sortBy,
                                                               @RequestParam(required = false) String sortDir) {
        Sort sort = SortParams.resolve(sortBy, sortDir);
        return ResponseEntity.ok(projectService.getProjects(currentUser.getId(), sort));
    }

    // Mapped before "/{id}" registration for the same reason UserController's "/me" is -- Spring
    // resolves the literal "as-client" segment ahead of the "{id}" variable pattern regardless of
    // declaration order, this is just for readability. Every Project here has myRole CLIENT;
    // deliberately a separate list from GET / above, not merged into it -- client access and
    // owner/collaborator access are different relationships (see docs/collaboration.md).
    @GetMapping("/as-client")
    public ResponseEntity<List<ProjectResponse>> getProjectsAsClient(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                        @RequestParam(required = false) String sortBy,
                                                                        @RequestParam(required = false) String sortDir) {
        Sort sort = SortParams.resolve(sortBy, sortDir);
        return ResponseEntity.ok(projectService.getProjectsAsClient(currentUser.getId(), sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                        @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProject(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                           @PathVariable UUID id,
                                                           @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal CustomUserDetails currentUser,
                                               @PathVariable UUID id) {
        projectService.deleteProject(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // Lives here rather than under /api/v1/assets since the class-level @RequestMapping there
    // would double the "/api/v1" prefix -- the underlying logic (including the owner/VIEW/EDIT
    // access check) is still entirely in AssetService, this just wires the nested route to it.
    @GetMapping("/{projectId}/assets")
    public ResponseEntity<List<AssetResponse>> getProjectAssets(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                   @PathVariable UUID projectId) {
        return ResponseEntity.ok(assetService.getProjectAssets(currentUser.getId(), projectId));
    }
}
