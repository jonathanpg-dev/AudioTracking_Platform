package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// A single append-only record of "this User did this thing, possibly to this Asset/Project" --
// the raw event log everything in AnalyticsQueryService aggregates from. Never updated after
// creation, never deleted by anything in this app. See docs/analytics.md.
@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "analytics_event", indexes = {
        @Index(name = "idx_analytics_event_user_timestamp", columnList = "user_id, timestamp"),
        @Index(name = "idx_analytics_event_type_timestamp", columnList = "event_type, timestamp"),
        @Index(name = "idx_analytics_event_asset_type", columnList = "asset_id, event_type"),
        @Index(name = "idx_analytics_event_project_type", columnList = "project_id, event_type")
})
public class AnalyticsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    // The authenticated User who performed the action -- never accepted from a request body (see
    // AnalyticsServiceImpl). A real FK is safe here: unlike Asset/Project, Users are never
    // deleted anywhere in this app.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalyticsEventType eventType;

    // Deliberately a plain UUID, NOT a @ManyToOne/foreign key. Asset and Project ARE deletable
    // (unlike User above), and this event must survive that deletion untouched -- no cascade
    // delete, no FK violation, no cleanup code needed anywhere else in the app. The tradeoff:
    // this id can point at a row that no longer exists, so anything resolving it back to a
    // title/name must treat "not found" as "deleted", not as a bug. See docs/analytics.md.
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "project_id")
    private UUID projectId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
}
