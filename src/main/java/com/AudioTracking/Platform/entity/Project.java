package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

// Asset association is deliberately not on this entity yet — it lands in the next vertical
// slice (Asset-Project) once Project itself is verified end to end.
@Entity
@Data
// Id-based equality, consistent with Asset/Tag — see Tag.java for the full reasoning. Project
// isn't in a Set anywhere yet, but applying this uniformly avoids the same class of bug
// resurfacing the moment one gets added later.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "project", indexes = {
        @Index(name = "idx_project_user_id", columnList = "user_id")
})
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
