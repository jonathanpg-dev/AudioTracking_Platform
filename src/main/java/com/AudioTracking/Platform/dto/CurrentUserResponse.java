package com.AudioTracking.Platform.dto;

import java.time.Instant;
import java.util.UUID;

// GET /users/me only -- deliberately a different, richer shape than the plain UserResponse every
// other user-lookup endpoint returns (GET /users, GET /users/{id}), since the two extra fields
// below are only ever meaningful for "tell me about myself" and would just be wasted extra
// queries per row for a list of other users.
public record CurrentUserResponse(
        UUID id,
        String username,
        String email,
        Instant createdAt,
        // True when this account owns nothing of its own (no Project, Asset, Collection, Tag, or
        // Client) AND is linked as at least one Client's login (see Client.linkedUser) AND hasn't
        // explicitly opted out of the simplified view -- i.e. an account that exists purely to
        // give a client view access, never a "real" user in their own right. The frontend renders
        // the simplified client-only shell for these. Computed live on every call, not stored, so
        // it flips to false the moment either thing happens: the account creates something of its
        // own (automatic, no action needed), or it calls POST /users/me/creator-mode ("Become a
        // creator too" -- explicit, and doesn't require owning anything yet). See
        // UserServiceImpl#getCurrentUser/#unlockCreatorMode.
        boolean isClientOnly,
        // True whenever this account is the linked login for at least one Client record, whether
        // or not isClientOnly is also true. A "full" user who is ALSO someone's client (isClientOnly
        // false, isLinkedAsClient true) still gets the extra "Client Projects" page. See
        // docs/collaboration.md.
        boolean isLinkedAsClient
) {
}
