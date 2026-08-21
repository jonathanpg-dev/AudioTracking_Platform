package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    // Used when deleting a Client: every Project pointing at it must be unassigned (not deleted)
    // first, same pattern as AssetRepository.findAllByProjectId for Project deletion.
    List<Project> findAllByClientId(UUID clientId);
}
