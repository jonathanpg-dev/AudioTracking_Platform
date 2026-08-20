package com.AudioTracking.Platform.exception;

// Wraps any storage-provider failure (upload/presign/delete). Deliberately provider-agnostic —
// R2StorageService is the only place that ever catches a raw AWS SDK exception and rethrows this;
// nothing above the storage layer should ever see an SdkException directly.
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
