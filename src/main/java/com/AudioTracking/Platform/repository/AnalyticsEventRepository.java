package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.AnalyticsEvent;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Every method here is deliberately a database aggregate (COUNT/GROUP BY), never a query that
// loads raw event rows into Java to be summed/ranked by hand -- see docs/analytics.md for why.
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    long countByUserIdAndEventType(UUID userId, AnalyticsEventType eventType);

    long countByUserIdAndTimestampBetween(UUID userId, Instant from, Instant to);

    long countByUserIdAndEventTypeAndTimestampBetween(UUID userId, AnalyticsEventType eventType, Instant from, Instant to);

    // One row per asset_id, ranked by how many times this user generated `eventType` against it
    // (e.g. ASSET_PLAYED -> "assets I've played most"). asset_id IS NOT NULL excludes event types
    // that never carry one (PROJECT_*, COLLECTION_CREATED, CLIENT_CREATED).
    @Query(value = """
            SELECT asset_id AS assetId, COUNT(*) AS eventCount
            FROM analytics_event
            WHERE user_id = :userId AND event_type = :eventType AND asset_id IS NOT NULL
            GROUP BY asset_id
            ORDER BY eventCount DESC
            """, nativeQuery = true)
    List<AssetEventCount> findTopAssetsByEventType(@Param("userId") UUID userId,
                                                     @Param("eventType") String eventType,
                                                     Pageable pageable);

    // One row per project_id, ranked by total event volume of any type against it (asset
    // uploads/plays/downloads/deletions within it, plus its own PROJECT_UPDATED/PROJECT_SHARED
    // events) -- this user's overall "most active project".
    @Query(value = """
            SELECT project_id AS projectId, COUNT(*) AS eventCount
            FROM analytics_event
            WHERE user_id = :userId AND project_id IS NOT NULL
            GROUP BY project_id
            ORDER BY eventCount DESC
            """, nativeQuery = true)
    List<ProjectEventCount> findTopProjectsByActivity(@Param("userId") UUID userId, Pageable pageable);

    // One row per calendar day in [from, to) with at least one matching event -- days with zero
    // events simply don't appear, callers fill the gaps. eventType is optional (null = any type);
    // handled as a native SQL CAST rather than JPQL since JPQL has no portable "truncate to day".
    @Query(value = """
            SELECT CAST(timestamp AS date) AS day, COUNT(*) AS eventCount
            FROM analytics_event
            WHERE user_id = :userId
              AND timestamp >= :from AND timestamp < :to
              AND (:eventType IS NULL OR event_type = :eventType)
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<DailyEventCount> findDailyActivity(@Param("userId") UUID userId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to,
                                             @Param("eventType") String eventType);

    interface AssetEventCount {
        UUID getAssetId();
        long getEventCount();
    }

    interface ProjectEventCount {
        UUID getProjectId();
        long getEventCount();
    }

    interface DailyEventCount {
        LocalDate getDay();
        long getEventCount();
    }
}
