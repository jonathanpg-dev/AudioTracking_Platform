package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Named "Collection" per the spec — be careful with imports in files that touch this: it
// shadows java.util.Collection if both are ever imported together. Not an issue as long as
// this package's files stick to List/Set (as they do) instead of the raw Collection interface.
@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "collection", indexes = {
        @Index(name = "idx_collection_user_id", columnList = "user_id")
})
public class Collection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Added for "order by date modified" search/sort support -- renaming a Collection is the
    // only mutation this entity has today (addAsset/removeAsset touch collection_assets, a
    // separate join table, not this row), so this is what "modified" means for a Collection.
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Collection owns this relationship (opposite of Asset<->Tag, where Asset was the owner):
    // deleting a Collection automatically cleans up its collection_assets rows via Hibernate.
    // Deleting an Asset does NOT get this for free — see AssetServiceImpl#deleteAsset.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "collection_assets",
            joinColumns = @JoinColumn(name = "collection_id"),
            inverseJoinColumns = @JoinColumn(name = "asset_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_collection_assets_pair", columnNames = {"collection_id", "asset_id"})
    )
    private Set<Asset> assets = new HashSet<>();

    // Same reasoning as Asset.addTag/removeTag: bidirectional associations need both in-memory
    // collections kept in sync manually, or whichever side wasn't just loaded goes stale within
    // the same transaction. See Asset.java for the full story of the bug this pattern avoids.
    public void addAsset(Asset asset) {
        assets.add(asset);
        asset.getCollections().add(this);
    }

    public void removeAsset(Asset asset) {
        assets.remove(asset);
        asset.getCollections().remove(this);
    }
}
