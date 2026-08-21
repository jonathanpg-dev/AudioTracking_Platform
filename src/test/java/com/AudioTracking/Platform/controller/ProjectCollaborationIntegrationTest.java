package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import com.AudioTracking.Platform.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 5: what a VIEW/EDIT collaborator (and an unrelated user) can actually DO with a shared
// Project's resources -- the behavioral core of the collaboration model. ProjectShare
// create/list/update/remove management itself is covered in ProjectShareControllerIntegrationTest;
// this class assumes shares already exist and exercises access through them.
//
// StorageService is mocked (@MockitoBean), same technique as AssetFileIntegrationTest -- this
// proves authorization is correctly evaluated BEFORE StorageService is ever reached, without
// needing real R2 credentials.
class ProjectCollaborationIntegrationTest extends BaseIntegrationTest {

    private static final byte[] VALID_WAV = "RIFF1234WAVEfmt ".getBytes();

    @MockitoBean
    private StorageService storageService;

    private String ownerToken;
    private String viewToken;
    private String editToken;
    private String outsiderToken;
    private String viewEmail;
    private String editEmail;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        ownerToken = registerAndLogin("collabOwner" + suffix, "collabOwner" + suffix + "@example.com", "password123");
        viewEmail = "collabView" + suffix + "@example.com";
        viewToken = registerAndLogin("collabView" + suffix, viewEmail, "password123");
        editEmail = "collabEdit" + suffix + "@example.com";
        editToken = registerAndLogin("collabEdit" + suffix, editEmail, "password123");
        outsiderToken = registerAndLogin("collabOutsider" + suffix, "collabOutsider" + suffix + "@example.com", "password123");

        projectId = createProject(ownerToken, "Shared EP");
        share(projectId, viewEmail, "VIEW");
        share(projectId, editEmail, "EDIT");
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void share(String projectId, String email, String permission) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + email + "\",\"permission\":\"" + permission + "\"}"))
                .andExpect(status().isCreated());
    }

    private String createAsset(String token, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beat\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String createAssetId(String token, String projectId) throws Exception {
        String body = createAsset(token, projectId);
        return objectMapper.readTree(body).get("id").asText();
    }

    private void uploadFile(String token, String assetId) throws Exception {
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ================= VIEW PERMISSION =================

    // 22. VIEW collaborator can retrieve shared Project.
    @Test
    void view_canRetrieveSharedProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shared EP"));
    }

    // 23. VIEW collaborator can retrieve/view Project resources.
    @Test
    void view_canListProjectAssets() throws Exception {
        createAssetId(ownerToken, projectId);

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/assets").header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // 24. VIEW collaborator can access/download authorized audio resources.
    @Test
    void view_canDownloadAuthorizedAudio() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);
        uploadFile(ownerToken, assetId);
        URI presigned = URI.create("https://r2.example.com/signed?sig=abc");
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class))).thenReturn(presigned);

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(presigned.toString()));
    }

    // 63 (R2): the access URL response never contains anything beyond url/expiresAt.
    @Test
    void view_downloadResponse_containsOnlyUrlAndExpiration_noCredentialsOrInternals() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);
        uploadFile(ownerToken, assetId);
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://r2.example.com/signed?sig=abc"));

        MvcResult result = mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.propertyNames()).containsExactlyInAnyOrder("url", "expiresAt");
    }

    // 25. VIEW collaborator cannot add resources.
    @Test
    void view_cannotAddAssetToProject() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + viewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isForbidden());
    }

    // 26. VIEW collaborator cannot modify resources.
    @Test
    void view_cannotModifyAsset() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + viewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isForbidden());
    }

    // 27. VIEW collaborator cannot delete resources.
    @Test
    void view_cannotDeleteAsset() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isForbidden());

        // Confirm it truly survived.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    // 31. VIEW collaborator cannot delete Project.
    @Test
    void view_cannotDeleteProject() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void view_cannotUpdateProjectMetadata_administrativeOperation() throws Exception {
        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + viewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed by collaborator","status":"PLANNING"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ================= EDIT PERMISSION =================

    // 32. EDIT collaborator can retrieve shared Project.
    @Test
    void edit_canRetrieveSharedProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isOk());
    }

    // 33. EDIT collaborator can access/download resources.
    @Test
    void edit_canDownloadAuthorizedAudio() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);
        uploadFile(ownerToken, assetId);
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://r2.example.com/signed?sig=abc"));

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + editToken))
                .andExpect(status().isOk());
    }

    // 34. EDIT collaborator can add resources/assets to Project.
    @Test
    void edit_canAddAssetToProject() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + editToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Beat\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    // 35. EDIT collaborator can modify resources/assets in Project.
    @Test
    void edit_canModifyAsset() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + editToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated by collaborator\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated by collaborator"));
    }

    // 36. EDIT collaborator can delete resources/assets in Project.
    @Test
    void edit_canDeleteAsset() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void edit_deletingAssetWithFile_stillCleansUpStorage() throws Exception {
        // Confirms Phase 4's R2 cleanup logic still fires correctly when the caller is a
        // collaborator, not just the owner.
        String assetId = createAssetId(ownerToken, projectId);
        uploadFile(ownerToken, assetId);

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isNoContent());

        verify(storageService).delete(anyString());
    }

    // 40. EDIT collaborator cannot delete Project.
    @Test
    void edit_cannotDeleteProject() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void edit_cannotUpdateProjectMetadata_administrativeOperation() throws Exception {
        // "EDIT means creative resource collaboration, not administrative control" -- name/status
        // changes are owner-only even for an EDIT collaborator.
        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + editToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed by collaborator","status":"PLANNING"}
                                """))
                .andExpect(status().isForbidden());
    }

    // 42. EDIT collaborator cannot modify resources outside the shared Project.
    @Test
    void edit_cannotModifyAssetInAnUnrelatedProject() throws Exception {
        String unrelatedProjectId = createProject(ownerToken, "Private Project"); // NOT shared with anyone
        String unrelatedAssetId = createAssetId(ownerToken, unrelatedProjectId);

        mockMvc.perform(put("/api/v1/assets/" + unrelatedAssetId)
                        .header("Authorization", "Bearer " + editToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void edit_cannotMoveAssetIntoAnUnrelatedProject() throws Exception {
        // Even for an asset they're allowed to edit, an EDIT collaborator can't reassign it into
        // a Project they have no access to -- resolveAssignableProjectOrNull requires EDIT there too.
        String assetId = createAssetId(ownerToken, projectId);
        String unrelatedProjectId = createProject(ownerToken, "Private Project");

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + editToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beat\",\"assetType\":\"BEAT\",\"projectId\":\"" + unrelatedProjectId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ================= OWNERSHIP =================

    // 43. Project owner retains ownership after sharing.
    @Test
    void ownership_projectOwnerUnaffectedBySharing() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        // Owner-only listing still shows it as theirs.
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1));
        // Collaborators never see it in THEIR "my projects" list -- they only reach it directly.
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + editToken))
                .andExpect(jsonPath("$").isEmpty());
    }

    // 44/46. Asset ownership remains correct after sharing; a collaborator cannot transfer it.
    @Test
    void ownership_editCollaboratorsCreatedAsset_isOwnedByThem_notTheProjectOwner() throws Exception {
        // Proven behaviorally: an asset the EDIT collaborator created keeps working for them even
        // after their project access is revoked entirely -- which is only possible if they, not
        // the project owner, are its actual owner (findAccessibleOrThrow's owner-check short-circuits
        // before ever consulting the (now nonexistent) share).
        String assetId = createAssetId(editToken, projectId);

        // Owner revokes the EDIT collaborator's share.
        MvcResult shares = mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        String shareId = null;
        for (var node : objectMapper.readTree(shares.getResponse().getContentAsString())) {
            if (node.get("email").asText().equals(editEmail)) {
                shareId = node.get("id").asText();
            }
        }
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/shares/" + shareId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // The former collaborator can still reach the asset THEY created, directly by id.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isOk());
    }

    // 45. Collaborator cannot modify ownership (no such field exists on the request DTOs at all,
    // so there's nothing to bind to -- proven by confirming the original owner keeps full access
    // after a collaborator's update, i.e. nothing about ownership silently changed).
    @Test
    void ownership_editCollaboratorUpdatingAsset_neverAffectsWhoOwnsIt() throws Exception {
        String assetId = createAssetId(ownerToken, projectId); // owned by the project owner

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + editToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Edited\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isOk());

        // Revoke the collaborator entirely, then confirm the ORIGINAL owner still has full
        // (owner-level) access -- proving ownership never silently moved to the collaborator.
        revokeShare(projectId, editEmail);
        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    private void revokeShare(String projectId, String email) throws Exception {
        MvcResult shares = mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        for (var node : objectMapper.readTree(shares.getResponse().getContentAsString())) {
            if (node.get("email").asText().equals(email)) {
                mockMvc.perform(delete("/api/v1/projects/" + projectId + "/shares/" + node.get("id").asText())
                                .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isNoContent());
                return;
            }
        }
        throw new IllegalStateException("No share found for " + email);
    }

    // ================= UNRELATED USERS =================

    // 47. Unrelated User cannot retrieve shared Project.
    @Test
    void unrelated_cannotRetrieveProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    // 48. Unrelated User cannot access Project resources.
    @Test
    void unrelated_cannotListProjectAssets() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/assets").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    // 49. Unrelated User cannot download associated audio.
    @Test
    void unrelated_cannotDownloadAudio_neverCallsStorage() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);
        uploadFile(ownerToken, assetId);

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());

        verify(storageService, never()).generatePresignedDownloadUrl(anyString(), any(Duration.class));
    }

    // 50. Unrelated User cannot modify Project resources.
    @Test
    void unrelated_cannotModifyAsset() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isNotFound());
    }

    // ================= SHARE REVOCATION =================

    // 53. Removed collaborator can no longer access Project.
    @Test
    void revocation_removedCollaboratorLosesProjectAccess() throws Exception {
        revokeShare(projectId, editEmail);

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isNotFound());
    }

    // 54. Removed collaborator can no longer access Project resources (an asset owned by the
    // PROJECT OWNER, not the ex-collaborator -- see ownership_editCollaboratorsCreatedAsset... for
    // why an asset the collaborator themselves created behaves differently).
    @Test
    void revocation_removedCollaboratorLosesResourceAccess() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);
        revokeShare(projectId, editEmail);

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + editToken))
                .andExpect(status().isNotFound());
    }

    // 55. Removed collaborator can no longer download audio.
    @Test
    void revocation_removedCollaboratorLosesDownloadAccess() throws Exception {
        String assetId = createAssetId(ownerToken, projectId);
        uploadFile(ownerToken, assetId);
        revokeShare(projectId, editEmail);

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + editToken))
                .andExpect(status().isNotFound());
    }

    // ================= SECURITY =================

    // 56/57. Unauthenticated requests rejected; resource-id manipulation across all collaboration
    // endpoints produces the same 404/403 pattern proven throughout this class.
    @Test
    void collaborationEndpoints_withoutAuth_return401() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/assets")).andExpect(status().isUnauthorized());
    }
}
