package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.project.ProjectShareResponse;
import com.AudioTracking.Platform.entity.ProjectShare;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectShareMapper {

    public ProjectShareResponse toResponse(ProjectShare share) {
        return new ProjectShareResponse(
                share.getId(),
                share.getUser().getId(),
                share.getUser().getUsername(),
                share.getUser().getEmail(),
                share.getPermission(),
                share.getCreatedAt());
    }

    public List<ProjectShareResponse> toResponseList(List<ProjectShare> shares) {
        return shares.stream().map(this::toResponse).toList();
    }
}
