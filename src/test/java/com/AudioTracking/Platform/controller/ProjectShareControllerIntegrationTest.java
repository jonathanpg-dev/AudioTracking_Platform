package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 5: ProjectShare management itself (create/list/update/remove) -- exclusively owner-only,
// per docs/collaboration.md. What a VIEW/EDIT collaborator can actually DO with their access is
// covered separately in ProjectCollaborationIntegrationTest.
class ProjectShareControllerIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String collaboratorToken;
    private String collaboratorEmail;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        ownerToken = registerAndLogin("shareOwner" + suffix, "shareOwner" + suffix + "@example.com", "password123");
        collaboratorEmail = "collaborator" + suffix + "@example.com";
        collaboratorToken = registerAndLogin("collaborator" + suffix, collaboratorEmail, "password123");
        outsiderToken = registerAndLogin("outsider" + suffix, "outsider" + suffix + "@example.com", "password123");
    }

    private String createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Collab EP"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult shareProject(String ownerToken, String projectId, String email, String permission) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + email + "\",\"permission\":\"" + permission + "\"}"))
                .andReturn();
    }

    // 15/16. Project owner can share a Project, choosing VIEW.
    @Test
    void createShare_withView_returnsShareWithCollaboratorDetails() throws Exception {
        String projectId = createProject(ownerToken);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + collaboratorEmail + "\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(collaboratorEmail))
                .andExpect(jsonPath("$.permission").value("VIEW"));
    }

    // 17. Project owner can choose EDIT.
    @Test
    void createShare_withEdit_succeeds() throws Exception {
        String projectId = createProject(ownerToken);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + collaboratorEmail + "\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("EDIT"));
    }

    @Test
    void createShare_targetUserDoesNotExist_returns404_createsNoUser() throws Exception {
        String projectId = createProject(ownerToken);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail":"nobody@example.com","permission":"VIEW"}
                                """))
                .andExpect(status().isNotFound());
    }

    // 18. Duplicate Project shares are rejected.
    @Test
    void createShare_duplicate_returns409_doesNotCreateSecondShare() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + collaboratorEmail + "\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].permission").value("VIEW")); // unchanged by the rejected attempt
    }

    // 19. Project owner can list collaborators.
    @Test
    void getShares_asOwner_listsCollaborators() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "EDIT");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(collaboratorEmail))
                .andExpect(jsonPath("$[0].permission").value("EDIT"));
    }

    @Test
    void getShares_neverExposesPasswordOrOtherAccountInternals() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    // 20. Project owner can change collaborator permission.
    @Test
    void updateShare_asOwner_changesPermission() throws Exception {
        String projectId = createProject(ownerToken);
        MvcResult shareResult = shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");
        String shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/projects/" + projectId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission":"EDIT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("EDIT"));
    }

    // 21. Project owner can remove collaborator.
    @Test
    void deleteShare_asOwner_removesCollaborator() throws Exception {
        String projectId = createProject(ownerToken);
        MvcResult shareResult = shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");
        String shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteShare_doesNotDeleteTheUserOrTheProject() throws Exception {
        String projectId = createProject(ownerToken);
        MvcResult shareResult = shareProject(ownerToken, projectId, collaboratorEmail, "EDIT");
        String shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // The (former) collaborator's own account is untouched.
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + collaboratorToken))
                .andExpect(status().isOk());
        // The Project itself still exists for the owner.
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    // Deleting a Project with active shares must not fail with a foreign-key violation, and must
    // genuinely remove the ProjectShare rows -- not just orphan them. Project.shares' cascade is
    // what makes this work: see Project.java.
    @Test
    void deletingProjectWithActiveShares_succeeds_andCollaboratorLosesAccessEntirely() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "EDIT");

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + collaboratorToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // --- Security: share-management endpoints are owner-only, full stop ---

    @Test
    void createShare_calledByViewCollaborator_isRejected() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");
        String outsiderEmail = "outsider2-" + System.nanoTime() + "@example.com";
        registerAndLogin("outsider2-" + System.nanoTime(), outsiderEmail, "password123");

        // The VIEW collaborator tries to add a third user themselves.
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + collaboratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + outsiderEmail + "\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createShare_calledByEditCollaborator_isRejected_cannotShareProject() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "EDIT");
        String outsiderEmail = "outsider3-" + System.nanoTime() + "@example.com";
        registerAndLogin("outsider3-" + System.nanoTime(), outsiderEmail, "password123");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + collaboratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + outsiderEmail + "\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateShare_calledByViewCollaborator_cannotEscalateTheirOwnPermission() throws Exception {
        String projectId = createProject(ownerToken);
        MvcResult shareResult = shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");
        String shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asText();

        // The VIEW collaborator tries to grant themselves EDIT.
        mockMvc.perform(put("/api/v1/projects/" + projectId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + collaboratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission":"EDIT"}
                                """))
                .andExpect(status().isForbidden());

        // Confirm it truly never changed.
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$[0].permission").value("VIEW"));
    }

    @Test
    void updateShare_calledByEditCollaborator_isRejected() throws Exception {
        // Even an EDIT collaborator -- who can already touch resources -- has no say over shares,
        // including their own.
        String projectId = createProject(ownerToken);
        MvcResult shareResult = shareProject(ownerToken, projectId, collaboratorEmail, "EDIT");
        String shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/projects/" + projectId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + collaboratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission":"VIEW"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteShare_calledByCollaborator_isRejected_cannotRemoveOwnOrOthersShare() throws Exception {
        String projectId = createProject(ownerToken);
        MvcResult shareResult = shareProject(ownerToken, projectId, collaboratorEmail, "EDIT");
        String shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + collaboratorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getShares_calledByCollaborator_isRejected() throws Exception {
        String projectId = createProject(ownerToken);
        shareProject(ownerToken, projectId, collaboratorEmail, "VIEW");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + collaboratorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shareEndpoints_unrelatedUser_returns404_notForbidden() throws Exception {
        // Someone with NO relationship to the project at all still gets a 404, not a 403 -- 403 is
        // reserved for a caller who's already a collaborator and thus already knows the project exists.
        String projectId = createProject(ownerToken);

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + collaboratorEmail + "\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareEndpoints_withoutAuth_return401() throws Exception {
        String projectId = createProject(ownerToken);
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/shares")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"x@example.com\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isUnauthorized());
    }
}
