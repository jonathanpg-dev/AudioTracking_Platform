package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.AssetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // Backs GET /assets (with or without filters/pagination/sort — a null filter param means
    // "don't filter on this", so calling with everything null reproduces the old unfiltered
    // listing).
    //
    // LEFT JOIN (not the implicit inner join a plain "a.project.id" path would use) is required
    // on the project association: a.project is nullable, and without LEFT JOIN, assets with no
    // project would silently vanish even when projectId isn't being filtered on at all.
    //
    // Tag matching is AND, not OR (an asset must carry every tag in :tagIds, not just one) and is
    // expressed as a correlated subquery rather than a join: `(SELECT COUNT(t) FROM a.tags t
    // WHERE t.id IN :tagIds) = :tagCount` is true only when every id in :tagIds was found among
    // this asset's tags. That also means, unlike the old single-tagId join, there's no multi-
    // valued join left in this query — so no fan-out, and no SELECT DISTINCT needed. tagIds is
    // passed as null (never an empty list) when the filter is unused: a null bind parameter makes
    // `t.id IN :tagIds` valid-but-false SQL, whereas binding an empty Java List to an IN clause is
    // provider-dependent and best avoided. See AssetServiceImpl#getAssets.
    @Query(
            value = """
                    SELECT a FROM Asset a
                    LEFT JOIN a.project p
                    WHERE a.user.id = :userId
                      AND (:assetType IS NULL OR a.assetType = :assetType)
                      AND (:projectId IS NULL OR p.id = :projectId)
                      AND (:minBpm IS NULL OR a.bpm >= :minBpm)
                      AND (:maxBpm IS NULL OR a.bpm <= :maxBpm)
                      AND (:musicalKey IS NULL OR LOWER(a.musicalKey) = LOWER(CAST(:musicalKey AS string)))
                      AND (:audioFormat IS NULL OR a.audioFormat = :audioFormat)
                      AND (:minDurationSeconds IS NULL OR a.durationSeconds >= :minDurationSeconds)
                      AND (:maxDurationSeconds IS NULL OR a.durationSeconds <= :maxDurationSeconds)
                      AND (:tagIds IS NULL OR (SELECT COUNT(t) FROM a.tags t WHERE t.id IN :tagIds) = :tagCount)
                    """,
            countQuery = """
                    SELECT COUNT(a) FROM Asset a
                    LEFT JOIN a.project p
                    WHERE a.user.id = :userId
                      AND (:assetType IS NULL OR a.assetType = :assetType)
                      AND (:projectId IS NULL OR p.id = :projectId)
                      AND (:minBpm IS NULL OR a.bpm >= :minBpm)
                      AND (:maxBpm IS NULL OR a.bpm <= :maxBpm)
                      AND (:musicalKey IS NULL OR LOWER(a.musicalKey) = LOWER(CAST(:musicalKey AS string)))
                      AND (:audioFormat IS NULL OR a.audioFormat = :audioFormat)
                      AND (:minDurationSeconds IS NULL OR a.durationSeconds >= :minDurationSeconds)
                      AND (:maxDurationSeconds IS NULL OR a.durationSeconds <= :maxDurationSeconds)
                      AND (:tagIds IS NULL OR (SELECT COUNT(t) FROM a.tags t WHERE t.id IN :tagIds) = :tagCount)
                    """
    )
    Page<Asset> search(@Param("userId") UUID userId,
                        @Param("assetType") AssetType assetType,
                        @Param("projectId") UUID projectId,
                        @Param("tagIds") List<UUID> tagIds,
                        @Param("tagCount") long tagCount,
                        @Param("minBpm") Integer minBpm,
                        @Param("maxBpm") Integer maxBpm,
                        @Param("musicalKey") String musicalKey,
                        @Param("audioFormat") String audioFormat,
                        @Param("minDurationSeconds") Integer minDurationSeconds,
                        @Param("maxDurationSeconds") Integer maxDurationSeconds,
                        Pageable pageable);
}
