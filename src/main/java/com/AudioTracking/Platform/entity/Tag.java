package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Data
// Id-based equality: Tag now lives inside Asset's Set<Tag>, and @Data's default (all fields)
// breaks there — two Tag instances should be "the same tag" based on identity, not on
// whether every field happens to match, and it must stay stable even if a field changes
// after the object is already in a set.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "tag",
        uniqueConstraints = @UniqueConstraint(name = "uk_tag_user_name", columnNames = {"user_id", "name"}),
        indexes = @Index(name = "idx_tag_user_id", columnList = "user_id"))
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Inverse side of Asset.tags. Needed so deleting a Tag can remove it from each referencing
    // Asset's collection through Hibernate's normal object-graph management, rather than a bulk
    // query that bypasses the persistence context and leaves already-loaded Asset instances
    // pointing at a Tag Hibernate no longer knows about.
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private Set<Asset> assets = new HashSet<>();
}
