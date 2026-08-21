package com.AudioTracking.Platform.entity;

// Deliberately just these two levels — see docs/collaboration.md. The Project owner already has
// full ownership privileges outside this enum, so there's no OWNER/ADMIN value here.
public enum ProjectPermission {
    VIEW,
    EDIT
}
