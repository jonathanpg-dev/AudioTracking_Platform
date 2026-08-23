package com.AudioTracking.Platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    // Null for accounts created via Google login that have never set a local password.
    @Column
    private String passwordHash;

    // Google's stable 'sub' claim. Null for accounts that only ever used username/password.
    @Column(unique = true)
    private String googleId;

    // True once a client-only account has explicitly chosen "Become a creator too" -- see
    // UserService#unlockCreatorMode. Independent of actually owning anything yet: this is what
    // lets the full producer UI unlock immediately on request, rather than only ever flipping
    // automatically the moment the account creates its first Project (see
    // CurrentUserResponse#isClientOnly for that automatic path, which still also applies). One-way
    // -- there's no "go back to the simplified view" action.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean creatorModeUnlocked = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
