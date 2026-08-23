package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A Client's login access to the Project(s) they're assigned to -- auto-provisioned account
// creation, ProjectRole.CLIENT's view-only-plus-client-notes grant, and GET /users/me's
// isClientOnly/isLinkedAsClient signals. See docs/collaboration.md.
class ClientAccessIntegrationTest extends BaseIntegrationTest {

    // Replaces the real Google verifier bean so logging in as the auto-provisioned client account
    // (loginAsProvisionedClient below) never makes a network call to Google -- same technique as
    // AuthControllerIntegrationTest.
    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private String ownerToken;
    private String projectId;
    private String assetId;
    private String clientEmail;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        ownerToken = registerAndLogin("clientOwner" + suffix, "clientOwner" + suffix + "@example.com", "password123");
        clientEmail = "producer.client" + suffix + "@example.com";

        projectId = createProject(ownerToken, "Client Project");
        String clientId = createClientWithEmail(ownerToken, "Studio Client", clientEmail);
        assignClient(ownerToken, projectId, "Client Project", clientId);
        assetId = createAsset(ownerToken, projectId, "Client-Visible Asset");
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

    private String createClientWithEmail(String token, String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void assignClient(String token, String projectId, String projectName, String clientId) throws Exception {
        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + projectName + "\",\"status\":\"PLANNING\",\"clientId\":\"" + clientId + "\"}"))
                .andExpect(status().isOk());
    }

    private String createAsset(String token, String projectId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // Logs in as the account auto-provisioned for clientEmail in setUp() by going through the
    // real POST /api/v1/auth/google endpoint (Google's own verification stubbed out) -- proves
    // the auto-provisioned account is genuinely reachable via Google login by that same email,
    // not just a database row with the right shape.
    private String loginAsProvisionedClient() throws Exception {
        return loginViaGoogle(clientEmail);
    }

    private String loginViaGoogle(String email) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-" + email);
        payload.setEmail(email);
        payload.setEmailVerified(true);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idToken);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"fake-token-for-" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // --- Auto-provisioning ---

    @Test
    void creatingClientWithNewEmail_provisionsAnAccountReachableViaGoogleLogin() throws Exception {
        String clientToken = loginAsProvisionedClient();

        // If provisioning had silently failed, there'd be no account for this email at all and
        // Google login would have created a brand-new, unrelated one with no Project access.
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("CLIENT"));
    }

    @Test
    void creatingClientWithExistingUsersEmail_linksToThatAccount_grantsThemAccess() throws Exception {
        long suffix = System.nanoTime();
        String existingEmail = "already.registered" + suffix + "@example.com";
        String existingUserToken = registerAndLogin("alreadyReal" + suffix, existingEmail, "password123");

        String secondProjectId = createProject(ownerToken, "Second Project");
        String secondClientId = createClientWithEmail(ownerToken, "Existing User As Client", existingEmail);
        assignClient(ownerToken, secondProjectId, "Second Project", secondClientId);

        // The pre-existing account -- not a newly minted one -- now has CLIENT access.
        mockMvc.perform(get("/api/v1/projects/" + secondProjectId).header("Authorization", "Bearer " + existingUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("CLIENT"));
    }

    // --- View access ---

    @Test
    void clientCanViewTheProjectAndItsAssets_withRoleClient() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("CLIENT"));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/assets").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());
    }

    @Test
    void getProjectsAsClient_listsTheAssignedProject_withRoleClient() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(get("/api/v1/projects/as-client").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(projectId))
                .andExpect(jsonPath("$[0].myRole").value("CLIENT"));
    }

    @Test
    void getProjects_ownedAndSharedListDoesNotIncludeClientAccessProjects() throws Exception {
        // The regular GET /projects (owned + collaborator-shared) is a deliberately separate list
        // from GET /projects/as-client -- see docs/collaboration.md.
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- Write restrictions: view-only, plus client notes, nothing else ---

    @Test
    void clientCannotModifyTheProject() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\",\"status\":\"PLANNING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotDeleteTheProject() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotModifyOrDeleteAssets() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotAddNewAssetsToTheProject() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isForbidden());
    }

    // --- Client notes: the one thing a client CAN write ---

    @Test
    void clientCanWriteClientNotesOnAnAssetInTheirProject() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(put("/api/v1/assets/" + assetId + "/client-notes")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientNotes\":\"Love the intro, can we extend the outro?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientNotes").value("Love the intro, can we extend the outro?"));

        // Visible to the owner too -- client notes are readable by anyone with view+ access.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientNotes").value("Love the intro, can we extend the outro?"));
    }

    @Test
    void ownerCannotWriteClientNotes_onlyTheClientCan() throws Exception {
        mockMvc.perform(put("/api/v1/assets/" + assetId + "/client-notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientNotes\":\"Owner trying to write this\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unrelatedUserCannotWriteClientNotes_getsNotFound() throws Exception {
        long suffix = System.nanoTime();
        String outsiderToken = registerAndLogin("clientOutsider" + suffix, "clientOutsider" + suffix + "@example.com", "password123");

        mockMvc.perform(put("/api/v1/assets/" + assetId + "/client-notes")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientNotes\":\"Sneaky\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientNotesOnAssetWithNoProject_returnsNotFound() throws Exception {
        String clientToken = loginAsProvisionedClient();
        MvcResult standalone = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Standalone\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String standaloneAssetId = objectMapper.readTree(standalone.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/assets/" + standaloneAssetId + "/client-notes")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientNotes\":\"Sneaky\"}"))
                .andExpect(status().isNotFound());
    }

    // --- GET /users/me signals ---

    @Test
    void freshlyProvisionedClientAccount_reportsClientOnly() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClientOnly").value(true))
                .andExpect(jsonPath("$.isLinkedAsClient").value(true));
    }

    @Test
    void ownerAccount_reportsNeitherClientOnlyNorLinkedAsClient() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClientOnly").value(false))
                .andExpect(jsonPath("$.isLinkedAsClient").value(false));
    }

    @Test
    void clientAccountThatLaterOwnsAProject_stopsReportingClientOnly() throws Exception {
        // "Vice versa": a client-only account becomes a full user the moment it creates something
        // of its own -- no explicit upgrade action, no stored flag to flip. See CurrentUserResponse.
        String clientToken = loginAsProvisionedClient();
        createProject(clientToken, "Their Own Project");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClientOnly").value(false))
                .andExpect(jsonPath("$.isLinkedAsClient").value(true));
    }

    // --- "Become a creator too" (POST /users/me/creator-mode) ---

    @Test
    void unlockCreatorMode_flipsClientOnlyToFalse_withoutOwningAnything() throws Exception {
        String clientToken = loginAsProvisionedClient();

        mockMvc.perform(post("/api/v1/users/me/creator-mode").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClientOnly").value(false))
                // Still their client's Project -- unlocking adds the full UI, it doesn't sever the
                // client relationship.
                .andExpect(jsonPath("$.isLinkedAsClient").value(true));
    }

    @Test
    void unlockCreatorMode_persistsAcrossRequests_notJustTheResponseBody() throws Exception {
        String clientToken = loginAsProvisionedClient();
        mockMvc.perform(post("/api/v1/users/me/creator-mode").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClientOnly").value(false))
                .andExpect(jsonPath("$.isLinkedAsClient").value(true));
    }

    @Test
    void unlockCreatorMode_stillGrantsAccessToTheirClientProjects_afterUnlocking() throws Exception {
        // Unlocking is purely additive -- CLIENT-role access to their existing Project(s) must
        // keep working exactly as before.
        String clientToken = loginAsProvisionedClient();
        mockMvc.perform(post("/api/v1/users/me/creator-mode").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/as-client").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].myRole").value("CLIENT"));
    }

    @Test
    void unlockCreatorMode_forAnAlreadyFullAccount_isHarmless() throws Exception {
        // A regular owner calling this (e.g. the endpoint reused/exposed generically) has nothing
        // to unlock -- isClientOnly was already false and stays false, isLinkedAsClient is
        // unaffected either way.
        mockMvc.perform(post("/api/v1/users/me/creator-mode").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClientOnly").value(false))
                .andExpect(jsonPath("$.isLinkedAsClient").value(false));
    }
}
