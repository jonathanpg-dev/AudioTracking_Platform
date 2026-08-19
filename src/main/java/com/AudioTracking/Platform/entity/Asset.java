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

@Entity
@Data
// See Tag for why: id-based equality is required once entities live inside a Set.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "asset", indexes = {
        @Index(name = "idx_asset_user_id", columnList = "user_id"),
        @Index(name = "idx_asset_project_id", columnList = "project_id")
})
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType assetType;

    private Integer bpm;

    @Column(length = 30)
    private String musicalKey;

    private Integer durationSeconds;

    private Long fileSizeBytes;

    @Column(length = 10)
    private String audioFormat;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // An asset can optionally belong to one project — nullable, unlike the required user FK
    // above. No ON DELETE behavior is configured here on purpose: ProjectServiceImpl explicitly
    // unassigns every affected asset (sets this to null) before deleting a project, within the
    // same transaction, so the FK's default RESTRICT acts as a safety net rather than something
    // relied on to fire.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    // Asset owns the relationship: association rows are managed here, and deleting an Asset
    // automatically cleans up its asset_tags rows (Hibernate handles this for the owning side).
    // Deleting a Tag does NOT get this for free — see TagRepository/TagServiceImpl.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "asset_tags",
            joinColumns = @JoinColumn(name = "asset_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_asset_tags_pair", columnNames = {"asset_id", "tag_id"})
    )
    private Set<Tag> tags = new HashSet<>();

    // Bidirectional associations must keep BOTH in-memory collections in sync — JPA does not
    // do this automatically, it only translates the owning side to SQL. Mutating tags directly
    // (e.g. asset.getTags().add(tag)) silently leaves Tag.assets stale within the same
    // persistence context, which is exactly what caused a real bug here: a since-fixed
    // TransientPropertyValueException when a Tag was deleted after being attached, because
    // Tag's inverse collection never reflected the association in the first place.
    public void addTag(Tag tag) {
        tags.add(tag);
        tag.getAssets().add(this);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.getAssets().remove(this);
    }

    // Inverse side of Collection.assets — Collection owns that relationship (opposite of the
    // tags relationship above, which Asset owns). See Collection.java and
    // AssetServiceImpl#deleteAsset for why this collection needs to exist and be walked
    // explicitly when an Asset is deleted.
    @ManyToMany(mappedBy = "assets", fetch = FetchType.LAZY)
    private Set<Collection> collections = new HashSet<>();
}
