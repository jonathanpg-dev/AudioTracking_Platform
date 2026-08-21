package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.AssetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    // Scoping the lookup by both id AND owner in one query means a wrong id and someone
    // else's id produce the exact same "not found" result — no separate ownership check needed.
    Optional<Asset> findByIdAndUserId(UUID id, UUID userId);

    List<Asset> findAllByProjectId(UUID projectId);

    long countByUserId(UUID userId);

    // COALESCE handles the "user has zero assets, or every asset has a null fileSizeBytes"
    // case -- SUM() over zero rows returns SQL NULL, not 0.
    @Query("SELECT COALESCE(SUM(a.fileSizeBytes), 0) FROM Asset a WHERE a.user.id = :userId")
    long sumFileSizeBytesByUserId(@Param("userId") UUID userId);

    // One row per project with at least one asset, for "assets per project" -- a single
    // aggregate query instead of looping findAllByProjectId per project (N+1).
    @Query("SELECT a.project.id AS projectId, COUNT(a) AS assetCount FROM Asset a " +
            "WHERE a.user.id = :userId AND a.project IS NOT NULL GROUP BY a.project.id")
    List<ProjectAssetCount> countAssetsGroupedByProject(@Param("userId") UUID userId);

    interface ProjectAssetCount {
        UUID getProjectId();
        long getAssetCount();
    }

    // Backs GET /assets (with or without filters/pagination — a null filter param means "don't
    // filter on this", so calling with everything null reproduces the old unfiltered listing).
    //
    // LEFT JOIN (not the implicit inner join a plain "a.project.id"/"a.tags" path would use) is
    // required on both associations: a.project is nullable, and without LEFT JOIN, assets with
    // no project would silently vanish even when projectId isn't being filtered on at all. Same
    // reasoning for tags. SELECT DISTINCT is required because the tags join is multi-valued —
    // without it, an asset with 3 tags would appear 3 times in a paginated result.
    @Query(
            value = """
                    SELECT DISTINCT a FROM Asset a
                    LEFT JOIN a.project p
                    LEFT JOIN a.tags t
                    WHERE a.user.id = :userId
                      AND (:assetType IS NULL OR a.assetType = :assetType)
                      AND (:projectId IS NULL OR p.id = :projectId)
                      AND (:tagId IS NULL OR t.id = :tagId)
                      AND (:minBpm IS NULL OR a.bpm >= :minBpm)
                      AND (:maxBpm IS NULL OR a.bpm <= :maxBpm)
                      AND (:musicalKey IS NULL OR a.musicalKey = :musicalKey)
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT a) FROM Asset a
                    LEFT JOIN a.project p
                    LEFT JOIN a.tags t
                    WHERE a.user.id = :userId
                      AND (:assetType IS NULL OR a.assetType = :assetType)
                      AND (:projectId IS NULL OR p.id = :projectId)
                      AND (:tagId IS NULL OR t.id = :tagId)
                      AND (:minBpm IS NULL OR a.bpm >= :minBpm)
                      AND (:maxBpm IS NULL OR a.bpm <= :maxBpm)
                      AND (:musicalKey IS NULL OR a.musicalKey = :musicalKey)
                    """
    )
    Page<Asset> search(@Param("userId") UUID userId,
                        @Param("assetType") AssetType assetType,
                        @Param("projectId") UUID projectId,
                        @Param("tagId") UUID tagId,
                        @Param("minBpm") Integer minBpm,
                        @Param("maxBpm") Integer maxBpm,
                        @Param("musicalKey") String musicalKey,
                        Pageable pageable);
}
