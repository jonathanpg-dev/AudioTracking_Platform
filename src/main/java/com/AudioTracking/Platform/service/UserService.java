package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.CurrentUserResponse;
import com.AudioTracking.Platform.dto.UpdateUserRequest;
import com.AudioTracking.Platform.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<UserResponse> getAllUsers(Sort sort);

    Page<UserResponse> getUsers(Pageable pageable);

    UserResponse getUserById(UUID id);

    // GET /users/me only -- see CurrentUserResponse for why this is a separate method/shape
    // rather than reusing getUserById.
    CurrentUserResponse getCurrentUser(UUID id);

    // "Become a creator too" -- lets a client-only account unlock the full producer UI on demand,
    // without needing to create a Project first. One-way; see User#creatorModeUnlocked.
    CurrentUserResponse unlockCreatorMode(UUID id);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    void deleteUser(UUID id);
}
