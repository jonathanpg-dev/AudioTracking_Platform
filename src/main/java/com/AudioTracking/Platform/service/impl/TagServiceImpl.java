package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.tag.CreateTagRequest;
import com.AudioTracking.Platform.dto.tag.TagResponse;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Tag;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.TagMapper;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final TagMapper tagMapper;

    public TagServiceImpl(TagRepository tagRepository, UserRepository userRepository, TagMapper tagMapper) {
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.tagMapper = tagMapper;
    }

    @Override
    public TagResponse createTag(UUID ownerId, CreateTagRequest request) {
        if (tagRepository.existsByUserIdAndName(ownerId, request.name())) {
            throw new DuplicateResourceException("Tag '" + request.name() + "' already exists");
        }

        Tag tag = tagMapper.toEntity(request);
        tag.setUser(userRepository.getReferenceById(ownerId));
        return tagMapper.toResponse(tagRepository.save(tag));
    }

    @Override
    public List<TagResponse> getTags(UUID ownerId) {
        return tagMapper.toResponseList(tagRepository.findAllByUserIdOrderByNameAsc(ownerId));
    }

    @Override
    public TagResponse getTag(UUID ownerId, UUID tagId) {
        return tagMapper.toResponse(findOwnedOrThrow(ownerId, tagId));
    }

    @Override
    public TagResponse updateTag(UUID ownerId, UUID tagId, CreateTagRequest request) {
        Tag existing = findOwnedOrThrow(ownerId, tagId);

        if (!existing.getName().equals(request.name()) && tagRepository.existsByUserIdAndName(ownerId, request.name())) {
            throw new DuplicateResourceException("Tag '" + request.name() + "' already exists");
        }

        existing.setName(request.name());
        return tagMapper.toResponse(tagRepository.save(existing));
    }

    @Override
    @Transactional // association cleanup + the delete itself must succeed or fail together
    public void deleteTag(UUID ownerId, UUID tagId) {
        Tag existing = findOwnedOrThrow(ownerId, tagId);
        // Go through the managed object graph (Asset.removeTag keeps both sides of the
        // bidirectional collection in sync) rather than a bulk/native delete against asset_tags
        // directly — a bulk delete bypasses Hibernate's persistence context, which then throws
        // if anything already loaded in this transaction still references the tag.
        // Iterate a copy: removeTag() mutates existing.getAssets() as we go.
        for (Asset asset : new ArrayList<>(existing.getAssets())) {
            asset.removeTag(existing);
        }
        tagRepository.delete(existing);
    }

    private Tag findOwnedOrThrow(UUID ownerId, UUID tagId) {
        return tagRepository.findByIdAndUserId(tagId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag with id '" + tagId + "' not found"));
    }
}
