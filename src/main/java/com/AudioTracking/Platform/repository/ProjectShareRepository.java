package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.ProjectShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectShareRepository extends JpaRepository<ProjectShare, UUID> {

    List<ProjectShare> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    // The core access-check query: does this user have ANY share on this project, and if so
    // what permission. Backs every ProjectAccessService decision.
    Optional<ProjectShare> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    // Scoping by id AND project in one query, same "wrong id and unrelated id look identical"
    // pattern as every other owned lookup in this app — a share id from a different Project must
    // 404 exactly like one that doesn't exist at all.
    Optional<ProjectShare> findByIdAndProjectId(UUID id, UUID projectId);
}
