# Clients, Project collaboration & sharing (Phase 5)

## Three separate concepts, deliberately never merged

- **User** — a registered account. Can log in, can own things, can collaborate.
- **Client** — a person/organization a User works with. Never logs in, never owns anything,
  purely metadata attached to a User. `User 1 --- * Client`.
- **Collaborator** — a User who has been granted access to someone else's Project via a
  `ProjectShare`. Not a separate entity — any User can be a collaborator on any number of Projects.

**Ownership** (who a row belongs to) and **access** (who is allowed to interact with it) are kept
strictly separate. Sharing a Project never changes who owns it or who owns the Assets inside it —
see "Ownership never transfers" below.

## `ProjectAccessService` — the single authorization chokepoint

Every Phase 5 authorization decision — in `ProjectServiceImpl`, `AssetServiceImpl`,
`ProjectShareServiceImpl`, `ClientServiceImpl`'s Project-facing checks — goes through one
component: `ProjectAccessService`. Three methods, each returning the loaded `Project` on success:

| Method | Grants access to |
|---|---|
| `requireViewAccess` | owner, or any collaborator (VIEW or EDIT) |
| `requireEditAccess` | owner, or an EDIT collaborator specifically |
| `requireOwnerAccess` | owner only |

Two, and only two, failure modes:

- **`ResourceNotFoundException` (404)** — the caller has *no* relationship to the Project at all
  (or it doesn't exist). Matches the "wrong id and someone else's id look identical" pattern used
  everywhere else in this app: a stranger can't tell a Project from a nonexistent one.
- **`InsufficientPermissionException` (403)** — the caller *is* related to the Project (owner or
  some share exists) but doesn't have enough permission for this specific action. A VIEW
  collaborator already knows the Project exists (they can see it) — hiding an EDIT-only action
  behind a 404 wouldn't protect anything, so this gets a 403 instead.

## Permission levels

```
VIEW: view Project + resources, download audio
EDIT: VIEW, plus add/modify/delete resources — nothing administrative
OWNER (implicit, not a ProjectShare row): everything, including sharing/deleting the Project
```

`ProjectPermission` is deliberately just `VIEW`/`EDIT` — no `ADMIN`, no per-action grants. The
owner's privileges come from `Project.user`, not from a ProjectShare row of their own.

**EDIT does not mean administrative control.** `PUT /api/v1/projects/{id}` (name, description,
status, client) is owner-only (`requireOwnerAccess`) even for EDIT collaborators — it's Project
*metadata*, not a creative resource. EDIT only extends to Assets: add, modify, delete, download.
Deleting the Project, managing shares, and changing the client are all owner-only regardless of
permission level.

## Asset access through a shared Project

Pre-Phase-5, every Asset operation was a single `findByIdAndUserId(id, ownerId)` — access meant
ownership, full stop. `AssetServiceImpl` now has two lookup paths:

- `findOwnedOrThrow` (unchanged, owner-only) — still used by `addTag`/`removeTag`. Tags are their
  own independently-owned resource with no Project relationship at all; extending tag management
  to collaborators would require deciding whose tags a collaborator may apply, which this phase
  doesn't attempt to answer.
- `findAccessibleOrThrow(requesterId, assetId, VIEW|EDIT)` (new) — succeeds for the Asset's owner
  **or** a sufficiently-permissioned collaborator on `Asset.project`. Used by everything else:
  view, upload, download, update, delete.

An Asset with no Project at all is only ever reachable by its owner — there's nothing for a
collaborator to access it *through*.

`GET /api/v1/projects/{projectId}/assets` (new) exists because the general
`GET /api/v1/assets?projectId=...` listing is hard-scoped to `a.user.id = :userId` and structurally
can't serve a collaborator browsing someone else's Project. It's the only new listing endpoint —
`GET /api/v1/projects` (the owner's own list) stays owned-projects-only; a collaborator reaches a
shared Project directly by id, not through the owner's list.

## Ownership never transfers

- **Sharing a Project** never changes `Project.user`.
- **Creating an Asset** — the creator is always the owner, even when an EDIT collaborator creates
  it inside someone else's shared Project. `AssetServiceImpl#createAsset` always does
  `asset.setUser(requester)`, regardless of whose Project it's going into.
- **Modifying an Asset** never touches `Asset.user` — there's no owner/userId field on
  `UpdateAssetRequest` for a collaborator to even attempt to set.
- **Storage keys** are namespaced under the *Asset's* owner (`asset.getUser().getId()`), not
  necessarily the requester — an EDIT collaborator uploading into someone else's Asset must not
  scatter objects under their own `users/{id}/...` prefix.

One consequence worth knowing: because ownership access is checked *before* Project/share access
in `findAccessibleOrThrow`, a collaborator who created an Asset while they had EDIT access keeps
full access to that specific Asset even after their share is later revoked — because they
genuinely own it. Only Project-*mediated* access (assets they don't personally own) is cut off by
revocation. See `ownership_editCollaboratorsCreatedAsset_isOwnedByThem_notTheProjectOwner` in
`ProjectCollaborationIntegrationTest` for this proven end to end.

Known rough edge from this same interaction: `PUT /assets/{id}` is full-replace, so a revoked
former collaborator who still owns the Asset can view/delete it fine, but an update that
re-submits the *same* `projectId` will fail (`resolveAssignableProjectOrNull` re-checks EDIT access
on that project on every update, whether it's actually changing or not) — they'd need to omit
`projectId` to unassign it, or have their share restored, to edit it further. This mirrors how
full-replace semantics already worked pre-Phase-5; collaboration just makes it reachable through a
new path (losing access to a project you don't own, rather than only ever assigning your own).

## Deletion semantics

| Deleting... | Removes | Preserves |
|---|---|---|
| a Client | the Client row; unassigns (`client = null`) every Project that referenced it | those Projects |
| a Project | the Project row, its ProjectShare rows (cascade), unassigns its Assets | the Client, the Assets, Users, Tags, Collections |
| a ProjectShare | just that one collaborator's access | the User, the Project, its Assets |

`Project.shares` is a cascading `@OneToMany` (`CascadeType.ALL`, `orphanRemoval = true`) so
deleting a Project cleanly removes its ProjectShare rows without a manual loop, the same way
`Asset.tags` (owning side) cleans up `asset_tags` automatically.

## What Phase 5 deliberately does not do

- No `ADMIN`/`OWNER`/custom permission levels — just VIEW/EDIT, per the spec.
- No collaborator self-service — every share create/list/update/delete call is owner-only, with no
  exceptions, even for a share on a collaborator's own access.
- No automatic User or Client creation — sharing requires an already-registered User (looked up by
  email, a unique existing field — no new identity mechanism introduced) and fails with 404 if not
  found.
- No self-share prevention — an owner sharing a Project with themselves is inert (their `isOwner`
  check in `ProjectAccessService` always short-circuits before any share is ever consulted) and
  isn't guarded against explicitly; not worth a new exception type for a no-op edge case.
- Tag management is not extended to collaborators (see above).
