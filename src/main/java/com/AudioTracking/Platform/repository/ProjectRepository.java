package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Project;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // Backs GET /projects: every Project this user can reach, owned or shared with them (any
    // permission level) -- previously owned-only, see docs/collaboration.md. DISTINCT is required:
    // the LEFT JOIN on shares is unconditional (not itself filtered to :userId), so an owned
    // Project that also has *other* collaborators' shares would otherwise fan out into one row
    // per share. Sort-parameterized rather than a fixed OrderBy suffix, so callers can order by
    // date added or date modified, ascending or descending -- see SortParams and ProjectController.
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.shares s WHERE p.user.id = :userId OR s.user.id = :userId")
    List<Project> findAllAccessibleByUserId(@Param("userId") UUID userId, Sort sort);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    // Used when deleting a Client: every Project pointing at it must be unassigned (not deleted)
    // first, same pattern as AssetRepository.findAllByProjectId for Project deletion.
    List<Project> findAllByClientId(UUID clientId);

    long countByUserId(UUID userId);

    // Backs GET /projects/as-client: every Project whose assigned Client's login account
    // (Client.linkedUser) is this User -- deliberately a separate list from
    // findAllAccessibleByUserId above, not merged into it (owner/collaborator access and client
    // access are different relationships to the Project; see docs/collaboration.md).
    @Query("SELECT p FROM Project p WHERE p.client.linkedUser.id = :userId")
    List<Project> findAllByClientLinkedUserId(@Param("userId") UUID userId, Sort sort);
}
