package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

// A person/organization the user works with — deliberately NOT a User in the ownership sense: a
// Client never owns anything, and only ever exists as metadata attached to whichever User manages
// them. See Project.client for the (optional) other half of that relationship.
//
// A Client CAN log in, though, via `linkedUser` below — see that field and
// ClientServiceImpl#resolveLinkedUserOrNull for how that account gets there.
@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "client", indexes = {
        @Index(name = "idx_client_user_id", columnList = "user_id"),
        @Index(name = "idx_client_linked_user_id", columnList = "linked_user_id")
})
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 254)
    private String email;

    @Column(length = 150)
    private String company;

    @Column(length = 2000)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The User account this Client can log in as, in order to view (never edit) Projects
    // assigned to them (Project.client) in read-only mode, plus write their own client notes on
    // those Projects' Assets. Nullable and re-resolved on every create/update of this Client's
    // email (see ClientServiceImpl#resolveLinkedUserOrNull):
    //   - no email -> null, no login access at all.
    //   - email matches an existing User -> linked to that account as-is (never creates a
    //     duplicate, never touches that User's own password/googleId/content -- this is exactly
    //     how "a regular User is also someone's Client" ends up working, no separate flag needed).
    //   - email matches no User -> a new Google-login-only account is provisioned (no
    //     passwordHash, no googleId yet) with a generated username, the same shape a Google-login
    //     signup produces -- see AuthServiceImpl#linkOrCreateGoogleUser, which is exactly what
    //     lets that same person log in with Google later using this same email with zero changes
    //     needed on the login side.
    // This is deliberately a completely separate field from `user` above (the Client's OWNER, the
    // producer who added them) -- never conflate who manages a Client with who a Client's login
    // access belongs to.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_user_id")
    private User linkedUser;
}
