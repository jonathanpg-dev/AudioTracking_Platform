package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// Grants one User (never the owner — the owner already has full access outside this table)
// permission to access one Project. This is the join entity behind Project <-> User's effective
// many-to-many "who can access this project" relationship, carrying the extra permission
// attribute a plain @ManyToMany join table couldn't express.
//
// Sharing NEVER transfers ownership: Project.user and every Asset.user under it are completely
// untouched by a ProjectShare existing. See ProjectAccessService for how this is turned into an
// actual access decision, and docs/collaboration.md for the full model.
@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "project_share",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_share_project_user", columnNames = {"project_id", "user_id"}),
        indexes = {
                @Index(name = "idx_project_share_project_id", columnList = "project_id"),
                @Index(name = "idx_project_share_user_id", columnList = "user_id")
        })
public class ProjectShare {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // The collaborator — a registered User, never a Client (Client is a separate, non-login
    // concept entirely; see Client.java).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjectPermission permission;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
