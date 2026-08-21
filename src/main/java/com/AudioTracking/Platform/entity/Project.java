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

// Asset association is deliberately not on this entity yet — it lands in the next vertical
// slice (Asset-Project) once Project itself is verified end to end.
@Entity
@Data
// Id-based equality, consistent with Asset/Tag — see Tag.java for the full reasoning. Project
// isn't in a Set anywhere yet, but applying this uniformly avoids the same class of bug
// resurfacing the moment one gets added later.
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "project", indexes = {
        @Index(name = "idx_project_user_id", columnList = "user_id"),
        @Index(name = "idx_project_client_id", columnList = "client_id")
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

    // Optional — a Project may belong to zero or one Client (a personal project has none). No ON
    // DELETE behavior is configured here on purpose, same reasoning as Asset.project: ClientServiceImpl
    // explicitly unassigns every affected project (sets this to null) before deleting a Client,
    // within the same transaction, so the FK's default RESTRICT is a safety net, not something relied on.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    // Owning side of the Project<->User collaboration relationship (see ProjectShare). Cascading
    // the delete here is what satisfies "deleting a Project removes its ProjectShare rows" without
    // needing a manual loop in ProjectServiceImpl#deleteProject — Hibernate fires it automatically
    // on project delete, same as it does for Asset.tags on the owning side of that relationship.
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectShare> shares = new HashSet<>();
}
