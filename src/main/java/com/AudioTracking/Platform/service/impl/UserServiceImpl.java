package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.CurrentUserResponse;
import com.AudioTracking.Platform.dto.UpdateUserRequest;
import com.AudioTracking.Platform.dto.UserResponse;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.UserMapper;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.CollectionRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.TagRepository;
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
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AssetRepository assetRepository;
    private final CollectionRepository collectionRepository;
    private final TagRepository tagRepository;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, ClientRepository clientRepository,
                            ProjectRepository projectRepository, AssetRepository assetRepository,
                            CollectionRepository collectionRepository, TagRepository tagRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.assetRepository = assetRepository;
        this.collectionRepository = collectionRepository;
        this.tagRepository = tagRepository;
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
    public CurrentUserResponse getCurrentUser(UUID id) {
        User user = findUserOrThrow(id);
        boolean isLinkedAsClient = clientRepository.existsByLinkedUserId(id);
        boolean ownsSomethingOfTheirOwn = projectRepository.countByUserId(id) > 0
                || assetRepository.countByUserId(id) > 0
                || collectionRepository.countByUserId(id) > 0
                || tagRepository.countByUserId(id) > 0
                || clientRepository.countByUserId(id) > 0;
        boolean isClientOnly = isLinkedAsClient && !ownsSomethingOfTheirOwn && !user.isCreatorModeUnlocked();
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt(),
                isClientOnly, isLinkedAsClient);
    }

    @Override
    public CurrentUserResponse unlockCreatorMode(UUID id) {
        User user = findUserOrThrow(id);
        user.setCreatorModeUnlocked(true);
        userRepository.save(user);
        return getCurrentUser(id);
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
}
