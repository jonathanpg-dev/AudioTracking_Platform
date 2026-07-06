package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.CreateUserRequest;
import com.AudioTracking.Platform.dto.UpdateUserRequest;
import com.AudioTracking.Platform.dto.UserResponse;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.UserMapper;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        validateUnique(request.username(), request.email());
        User saved = userRepository.save(userMapper.toEntity(request));
        return userMapper.toResponse(saved);
    }

    @Override
    public List<UserResponse> createUsers(List<CreateUserRequest> requests) {
        requests.forEach(request -> validateUnique(request.username(), request.email()));
        List<User> users = requests.stream().map(userMapper::toEntity).toList();
        return userMapper.toResponseList(userRepository.saveAll(users));
    }

    @Override
    public List<UserResponse> getAllUsers(Sort sort) {
        return userMapper.toResponseList(userRepository.findAll(sort));
    }

    @Override
    public Page<UserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(findUserOrThrow(id));
    }

    @Override
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User existing = findUserOrThrow(id);

        if (!existing.getUsername().equals(request.username()) && userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (!existing.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already taken");
        }

        userMapper.updateEntity(request, existing);
        return userMapper.toResponse(userRepository.save(existing));
    }

    @Override
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User with id '" + id + "' not found");
        }
        userRepository.deleteById(id);
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));
    }

    private void validateUnique(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already taken");
        }
    }
}
