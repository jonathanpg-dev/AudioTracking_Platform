package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.tag.CreateTagRequest;
import com.AudioTracking.Platform.dto.tag.TagResponse;

import java.util.List;
import java.util.UUID;

public interface TagService {

    TagResponse createTag(UUID ownerId, CreateTagRequest request);

    List<TagResponse> getTags(UUID ownerId);

    TagResponse getTag(UUID ownerId, UUID tagId);

    TagResponse updateTag(UUID ownerId, UUID tagId, CreateTagRequest request);

    void deleteTag(UUID ownerId, UUID tagId);
}
