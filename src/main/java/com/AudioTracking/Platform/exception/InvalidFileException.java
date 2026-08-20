package com.AudioTracking.Platform.exception;

// Client-side file problems: missing, empty, unsupported format, content doesn't match its
// extension. Always the caller's fault -> 400, distinct from StorageException (the provider's fault).
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}
