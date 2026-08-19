package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.tag.CreateTagRequest;
import com.AudioTracking.Platform.dto.tag.TagResponse;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                  @Valid @RequestBody CreateTagRequest request) {
        TagResponse response = tagService.createTag(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(tagService.getTags(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getTag(@AuthenticationPrincipal CustomUserDetails currentUser,
                                               @PathVariable UUID id) {
        return ResponseEntity.ok(tagService.getTag(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> updateTag(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                  @PathVariable UUID id,
                                                  @Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.ok(tagService.updateTag(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@AuthenticationPrincipal CustomUserDetails currentUser,
                                           @PathVariable UUID id) {
        tagService.deleteTag(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
