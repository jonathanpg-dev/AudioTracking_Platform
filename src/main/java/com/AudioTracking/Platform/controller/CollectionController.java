package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.collection.CollectionResponse;
import com.AudioTracking.Platform.dto.collection.CreateCollectionRequest;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.CollectionService;
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
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> createCollection(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                 @Valid @RequestBody CreateCollectionRequest request) {
        CollectionResponse response = collectionService.createCollection(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CollectionResponse>> getCollections(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                     @RequestParam(required = false) String sortBy,
                                                                     @RequestParam(required = false) String sortDir) {
        Sort sort = SortParams.resolve(sortBy, sortDir);
        return ResponseEntity.ok(collectionService.getCollections(currentUser.getId(), sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollectionResponse> getCollection(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                              @PathVariable UUID id) {
        return ResponseEntity.ok(collectionService.getCollection(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CollectionResponse> updateCollection(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                 @PathVariable UUID id,
                                                                 @Valid @RequestBody CreateCollectionRequest request) {
        return ResponseEntity.ok(collectionService.updateCollection(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCollection(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                  @PathVariable UUID id) {
        collectionService.deleteCollection(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assets/{assetId}")
    public ResponseEntity<CollectionResponse> addAsset(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                         @PathVariable UUID id,
                                                         @PathVariable UUID assetId) {
        return ResponseEntity.ok(collectionService.addAsset(currentUser.getId(), id, assetId));
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    public ResponseEntity<CollectionResponse> removeAsset(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                            @PathVariable UUID id,
                                                            @PathVariable UUID assetId) {
        return ResponseEntity.ok(collectionService.removeAsset(currentUser.getId(), id, assetId));
    }
}
