package com.AudioTracking.Platform.util;

import org.springframework.data.domain.Sort;

import java.util.Set;

// Resolves the `sortBy`/`sortDir` query params shared by every "order by date added/modified"
// list endpoint (GET /assets, /projects, /collections) into a Spring Sort. Both params are
// optional; omitting either (or passing something unrecognized) falls back to createdAt
// descending -- the exact behavior each of those endpoints had before this existed, so an old
// client that never sends these params sees no change.
public final class SortParams {

    private static final Set<String> ALLOWED_FIELDS = Set.of("createdAt", "updatedAt");
    private static final String DEFAULT_FIELD = "createdAt";

    private SortParams() {
    }

    public static Sort resolve(String sortBy, String sortDir) {
        // Set.of(...) is an immutable set -- unlike HashSet, its contains() throws NPE on a null
        // argument rather than just returning false, so the null check must come first (sortBy is
        // null on every request that omits it, which is the common case).
        String field = (sortBy != null && ALLOWED_FIELDS.contains(sortBy)) ? sortBy : DEFAULT_FIELD;
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
