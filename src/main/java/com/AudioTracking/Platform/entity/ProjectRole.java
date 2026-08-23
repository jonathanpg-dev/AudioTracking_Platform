package com.AudioTracking.Platform.entity;

// The caller's relationship to a Project -- distinct from ProjectPermission, which only ever
// describes a ProjectShare row. OWNER is never persisted anywhere (ownership comes from
// Project.user, not a share), so it can't just be added as a ProjectPermission value.
//
// Response-only: exists purely so the frontend can render permission-aware UI (e.g. only an
// OWNER ever sees "Manage collaborators") without re-deriving access logic itself or guessing
// from a failed request. See ProjectAccessService#getRole and docs/collaboration.md.
public enum ProjectRole {
    OWNER,
    VIEW,
    EDIT,
    // The Project's assigned Client, logged in as their linked User (see Client.linkedUser).
    // Behaves like VIEW everywhere (requireViewAccess grants it, requireEditAccess rejects it)
    // except for one narrower additional capability VIEW doesn't have: writing client notes on
    // this Project's Assets. Never persisted as a ProjectPermission -- like OWNER, it's derived
    // (from Project.client.linkedUser, not a ProjectShare row) rather than stored.
    CLIENT
}
