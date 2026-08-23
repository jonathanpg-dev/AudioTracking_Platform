package com.AudioTracking.Platform.dto.asset;

import jakarta.validation.constraints.Size;

// Deliberately its own tiny request DTO, not folded into UpdateAssetRequest -- client notes are
// writable through a completely different authorization path (the Project's linked client only,
// never the owner or an EDIT collaborator) and shouldn't share a full-replace request shape with
// fields a client has no business touching. See AssetService#updateClientNotes.
public record UpdateClientNotesRequest(
        @Size(max = 2000)
        String clientNotes
) {
}
