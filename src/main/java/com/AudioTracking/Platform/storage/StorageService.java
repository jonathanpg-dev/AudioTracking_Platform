package com.AudioTracking.Platform.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

// Application-level storage abstraction. Everything above this interface (AssetService and up)
// works purely in terms of "objects identified by a key" — no provider-specific types (no AWS
// SDK classes, no R2 concepts) ever cross this boundary in either direction. That's what lets
// R2StorageService be swapped for a future S3StorageService without touching AssetService.
public interface StorageService {

    // Uploads (or overwrites) the object at `key`. contentLength must be known up front — the
    // caller reads it from the MultipartFile before opening the stream.
    void upload(String key, InputStream data, long contentLength, String contentType);

    // A short-lived, signed URL that grants temporary read access to a private object. Never
    // persisted — callers must request a fresh one each time access is needed.
    URI generatePresignedDownloadUrl(String key, Duration expiration);

    void delete(String key);
}
