package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.ProjectShare;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectShareRepository extends JpaRepository<ProjectShare, UUID> {

    List<ProjectShare> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    // Every Project shared *with* this user, across all Projects -- backs the batch role lookup
    // for GET /projects now that it includes shared Projects (see ProjectAccessService#getRoles).
    // One query instead of one findByProjectIdAndUserId per Project in the list.
    List<ProjectShare> findAllByUserId(UUID userId);

    // The core access-check query: does this user have ANY share on this project, and if so
    // what permission. Backs every ProjectAccessService decision.
    Optional<ProjectShare> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    // Scoping by id AND project in one query, same "wrong id and unrelated id look identical"
    // pattern as every other owned lookup in this app — a share id from a different Project must
    // 404 exactly like one that doesn't exist at all.
    Optional<ProjectShare> findByIdAndProjectId(UUID id, UUID projectId);

    // Collaboration analytics: current-state counts derived from live ProjectShare rows (not the
    // event log) -- "how many collaborator grants exist across projects I own" and "across how
    // many distinct projects". Both scoped by the PROJECT's owner, reusing the existing
    // property-path traversal (project.user.id) rather than a hand-written join.
    long countByProject_UserId(UUID ownerId);

    @Query("SELECT COUNT(DISTINCT ps.project.id) FROM ProjectShare ps WHERE ps.project.user.id = :ownerId")
    long countDistinctProjectsByProjectOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT ps.project.id AS projectId, ps.project.name AS projectName, COUNT(ps) AS shareCount " +
            "FROM ProjectShare ps WHERE ps.project.user.id = :ownerId " +
            "GROUP BY ps.project.id, ps.project.name ORDER BY COUNT(ps) DESC")
    List<ProjectShareCount> findMostSharedProjects(@Param("ownerId") UUID ownerId, Pageable pageable);

    interface ProjectShareCount {
        UUID getProjectId();
        String getProjectName();
        long getShareCount();
    }
}
