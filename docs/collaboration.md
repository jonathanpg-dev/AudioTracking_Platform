# Clients, Project collaboration & sharing (Phase 5)

## Three separate concepts, deliberately never merged

- **User** — a registered account. Can log in, can own things, can collaborate.
- **Client** — a person/organization a User works with. Never owns anything, purely metadata
  attached to a User (`User 1 --- * Client`). A Client *itself* still can't log in — but see
  "Client access" below: a Client can now be *linked* to a User account, and that User logs in
  as themselves, not as the Client.
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
| `requireViewAccess` | owner, the Project's linked client (see "Client access" below), or any collaborator (VIEW or EDIT) |
| `requireEditAccess` | owner, or an EDIT collaborator specifically — a linked client is explicitly rejected with 403, not silently excluded, so the "insufficient permission, not nonexistent" contract holds for them too |
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
CLIENT (implicit, not a ProjectShare row either): view Project + resources, download audio,
        plus write that Asset's clientNotes — nothing else. See "Client access" below.
```

`ProjectPermission` (the `ProjectShare` enum) is deliberately just `VIEW`/`EDIT` — no `ADMIN`, no
per-action grants. `ProjectRole` (what `ProjectResponse.myRole`/`ProjectAccessService.getRole`
actually return to a caller) is the wider `OWNER | VIEW | EDIT | CLIENT`, since the caller's own
relationship to a Project can be a share, ownership, or a client link, and the frontend renders
differently for all four. The owner's privileges come from `Project.user`, not from a ProjectShare
row of their own; CLIENT works the same way — from `Project.client.linkedUser`, not a ProjectShare.

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
can't serve a collaborator browsing someone else's Project.

`GET /api/v1/projects` includes both owned Projects and Projects shared with the caller (any
permission level) — `myRole` is resolved per-Project (`ProjectAccessService#getRoles`, a batch
counterpart to `getRole` that looks up all of the caller's shares in one query rather than one
round-trip per Project), so a collaborator's own OWNER Projects and their shared VIEW/EDIT ones
sit correctly labeled in the same list. This was originally owned-projects-only ("a collaborator
reaches a shared Project directly by id, not through the owner's list") but that meant a
collaborator's own Projects page never showed what had been shared with them — changed so
`GET /projects` matches what a collaborator actually needs to discover their shared work, the same
way most collaboration tools show a "shared with me" view.

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

## Client access — a Client's linked User can log in and view its Projects

A `Client` row can be linked to a `User` account (`Client.linkedUser`), resolved automatically
from the Client's email whenever a Client is created or updated
(`ClientServiceImpl#resolveLinkedUserOrNull`):

- blank/no email → `linkedUser` stays null (no change to today's owner-only-visible Client).
- email matches an existing User → linked to that account as-is, no new row created.
- email matches no User → a new, Google-login-only User is auto-provisioned right then
  (`provisionClientOnlyUser`: no `passwordHash`, no `googleId` yet — set the first time they
  actually sign in with Google, the same `linkOrCreateGoogleUser` find-by-email path
  `AuthServiceImpl` already used for Google sign-in, since the email now matches this row). The
  point is the account is reachable *before* the client has ever logged in — a producer adding a
  client by email is what creates it, not the client's first sign-in.

Re-resolved on every `updateClient` too, so editing a Client's email re-links it (or unlinks it,
if cleared).

`ProjectAccessService` grants a linked client `requireViewAccess` on every Project that Client is
attached to (`isLinkedClient`, checked ahead of the ProjectShare lookup in every method) — see the
permission table above. This is deliberately **not** a `ProjectShare` row: a client's access is
derived structurally from `Project.client`, so it can never drift out of sync with which Client a
Project actually has, and revoking it is just clearing the Project's client (or the Client's
email), not managing a separate grant.

`GET /api/v1/projects/as-client` lists Projects reached this way — deliberately a **separate**
endpoint from `GET /api/v1/projects` (owned + shared), not merged into it, so a dual-role account
(see below) gets a clean split between "my work" and "work I'm the client for" rather than one
list mixing four different `myRole` values together with no visual grouping. Because of that
split, `findAllAccessibleByUserId` never returns a client-access Project, and `getRoles` (the batch
counterpart used to label that list) never actually resolves `CLIENT` in practice — the check is
still there for consistency with `getRole`, but it's currently unreachable through `GET /projects`.

**Client notes** (`Asset.clientNotes`, one editable field, not a growing thread) are the one write
a CLIENT role gets: `AssetService#updateClientNotes` requires view access to the Asset's Project,
then separately re-checks that the requester is specifically *that* Project's linked client before
allowing the write — a VIEW/EDIT collaborator or the owner can read client notes (they're just
another Asset field in `AssetResponse`) but not write them, and the client can't write notes on a
Project they're not attached to.

**Client-only accounts.** `GET /api/v1/users/me` (`CurrentUserResponse`) reports two flags,
recomputed on every call:

- `isLinkedAsClient` — linked as the client on at least one Project.
- `isClientOnly` — linked as a client **and** owns nothing of their own (no Project, Asset,
  Collection, Tag, or Client row) **and** hasn't unlocked creator mode (see below). The frontend
  uses this to show a client-only account a deliberately minimal UI (see `docs/frontend.md`).

Two independent ways `isClientOnly` flips back to `false`, both requiring no explicit
"upgrade" flow on the owning-Project side:

- **Automatic** — the moment the account creates anything of its own (a Project, most commonly),
  it's simply no longer true that it "owns nothing." Nothing has to be set for this; it falls out
  of the same live query every time. This is what makes "a regular User also becomes a client, or
  vice versa" (the spec's own phrasing) just work rather than needing to be a state machine.
- **Explicit — `POST /api/v1/users/me/creator-mode`** ("Become a creator too" in the frontend
  nav). Sets `User.creatorModeUnlocked = true`, a real stored column, since unlike the automatic
  path there's no ownership row to derive the flip from — the whole point is unlocking the full UI
  *before* the account has created anything, not waiting for it to. One-way: there's no matching
  endpoint to lock it back. `isLinkedAsClient` is untouched either way, so a creator-unlocked
  account that's still someone's client keeps the "Client Projects" nav entry (see
  `docs/frontend.md`) — unlocking only ever adds the full UI on top, never removes the client
  relationship.

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
- No automatic User creation for **ProjectShare** collaborators — sharing still requires an
  already-registered User (looked up by email) and fails with 404 if not found. This is unrelated
  to Client linking above: a Client is auto-provisioned a User the moment it's created with an
  unrecognized email, but a collaborator invite is not — inviting a collaborator is an explicit
  "grant this specific existing account access" action, while adding a Client is "here's who this
  Project is for," and that person not having an account yet shouldn't block creating the Client.
- No self-share prevention — an owner sharing a Project with themselves is inert (their `isOwner`
  check in `ProjectAccessService` always short-circuits before any share is ever consulted) and
  isn't guarded against explicitly; not worth a new exception type for a no-op edge case.
- Tag management is not extended to collaborators (see above).
