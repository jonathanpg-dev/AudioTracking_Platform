package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.project.CreateProjectRequest;
import com.AudioTracking.Platform.dto.project.ProjectResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectRequest;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.ProjectService;
import jakarta.validation.Valid;
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

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                           @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(projectService.getProjects(currentUser.getId()));
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
}
