package com.AudioTracking.Platform.exception;

// Distinct from ResourceNotFoundException: this is for a caller who IS related to the resource
// (a VIEW collaborator hitting an EDIT-only action, or any collaborator hitting an owner-only
// action) and already legitimately knows it exists -> 403, not a 404 that pretends otherwise.
// A caller with NO relationship to the resource at all still gets ResourceNotFoundException, same
// as every other owned entity in this app.
public class InsufficientPermissionException extends RuntimeException {
    public InsufficientPermissionException(String message) {
        super(message);
    }
}
