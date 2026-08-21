package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.project.CreateProjectShareRequest;
import com.AudioTracking.Platform.dto.project.ProjectShareResponse;
import com.AudioTracking.Platform.dto.project.UpdateProjectShareRequest;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.ProjectShareService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Every endpoint here is owner-only, enforced in ProjectShareServiceImpl via
// ProjectAccessService#requireOwnerAccess -- a collaborator (VIEW or EDIT) can never successfully
// call any of these, even to manage their own share. See docs/collaboration.md.
@RestController
@RequestMapping("/api/v1/projects/{projectId}/shares")
public class ProjectShareController {

    private final ProjectShareService projectShareService;

    public ProjectShareController(ProjectShareService projectShareService) {
        this.projectShareService = projectShareService;
    }

    @PostMapping
    public ResponseEntity<ProjectShareResponse> createShare(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                              @PathVariable UUID projectId,
                                                              @Valid @RequestBody CreateProjectShareRequest request) {
        ProjectShareResponse response = projectShareService.createShare(currentUser.getId(), projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectShareResponse>> getShares(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                   @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectShareService.getShares(currentUser.getId(), projectId));
    }

    @PutMapping("/{shareId}")
    public ResponseEntity<ProjectShareResponse> updateShare(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                              @PathVariable UUID projectId,
                                                              @PathVariable UUID shareId,
                                                              @Valid @RequestBody UpdateProjectShareRequest request) {
        return ResponseEntity.ok(projectShareService.updateShare(currentUser.getId(), projectId, shareId, request));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> deleteShare(@AuthenticationPrincipal CustomUserDetails currentUser,
                                             @PathVariable UUID projectId,
                                             @PathVariable UUID shareId) {
        projectShareService.deleteShare(currentUser.getId(), projectId, shareId);
        return ResponseEntity.noContent().build();
    }
}
