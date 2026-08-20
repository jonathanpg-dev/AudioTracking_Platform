package com.AudioTracking.Platform.dto.asset;

import java.time.Instant;

// Deliberately never persisted — a presigned URL is generated fresh on every request and expires
// shortly after. expiresAt tells the client when to stop trusting/using this specific URL.
public record FileAccessResponse(
        String url,
        Instant expiresAt
) {
}
