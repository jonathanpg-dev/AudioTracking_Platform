package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.CurrentUserResponse;
import com.AudioTracking.Platform.dto.UpdateUserRequest;
import com.AudioTracking.Platform.dto.UserResponse;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Mapped before "/{id}" registration order doesn't actually matter here since "me" would
    // fail UUID conversion and 400 if it were ever matched by @PathVariable UUID id anyway, but
    // being explicit keeps intent obvious. The only way to reach this is the JWT of the caller
    // themselves -- there is no way to pass another user's id here.
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(userService.getCurrentUser(currentUser.getId()));
    }

    // "Become a creator too" -- see UserService#unlockCreatorMode. Same "only reachable via the
    // caller's own JWT" note as /me above applies here too.
    @PostMapping("/me/creator-mode")
    public ResponseEntity<CurrentUserResponse> unlockCreatorMode(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(userService.unlockCreatorMode(currentUser.getId()));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers(
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        Sort sort = sortBy == null
                ? Sort.unsorted()
                : Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);

        if (page != null && size != null) {
            List<UserResponse> content = userService.getUsers(PageRequest.of(page, size, sort)).getContent();
            return ResponseEntity.ok(content);
        }

        return ResponseEntity.ok(userService.getAllUsers(sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
