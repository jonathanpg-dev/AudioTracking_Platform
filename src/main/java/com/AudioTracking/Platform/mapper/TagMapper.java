package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.tag.CreateTagRequest;
import com.AudioTracking.Platform.dto.tag.TagResponse;
import com.AudioTracking.Platform.entity.Tag;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TagMapper {

    public Tag toEntity(CreateTagRequest request) {
        Tag tag = new Tag();
        tag.setName(request.name());
        return tag;
    }

    public TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt());
    }

    public List<TagResponse> toResponseList(List<Tag> tags) {
        return tags.stream().map(this::toResponse).toList();
    }
}
