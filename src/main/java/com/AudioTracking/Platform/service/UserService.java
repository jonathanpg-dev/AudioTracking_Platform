package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.CreateUserRequest;
import com.AudioTracking.Platform.dto.UpdateUserRequest;
import com.AudioTracking.Platform.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    List<UserResponse> createUsers(List<CreateUserRequest> requests);

    List<UserResponse> getAllUsers(Sort sort);

    Page<UserResponse> getUsers(Pageable pageable);

    UserResponse getUserById(UUID id);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    void deleteUser(UUID id);
}
