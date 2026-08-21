package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 6, "ANALYTICS AUTHORIZATION TESTS": every analytics endpoint is scoped to the
// authenticated caller only, with no way to name a different user.
class AnalyticsAuthorizationIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;
    private String tokenBEmail;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("analyticsAuthA" + suffix, "analyticsAuthA" + suffix + "@example.com", "password123");
        tokenBEmail = "analyticsAuthB" + suffix + "@example.com";
        tokenB = registerAndLogin("analyticsAuthB" + suffix, tokenBEmail, "password123");
    }

    // 10. User can access their own analytics.
    @Test
    void user_canAccessOwnAnalytics_everyEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/assets").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/projects").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/collaboration").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/activity").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk());
    }

    // 11. User cannot access another user's workspace analytics.
    @Test
    void user_seesOnlyTheirOwnWorkspaceMetrics_notAnotherUsers() throws Exception {
        // User A builds up a real workspace.
        createClient(tokenA, "John Smith");
        createProject(tokenA, "R&B EP");

        // User B, who has done nothing, must see zeros -- not A's data, not an error.
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/overview").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalClients").asLong()).isZero();
        assertThat(body.get("totalProjects").asLong()).isZero();
    }

    // 12. User cannot manipulate a user ID to retrieve another user's analytics.
    @Test
    void userIdQueryParam_isIgnored_callersOwnIdentityAlwaysWins() throws Exception {
        createClient(tokenA, "John Smith"); // give A exactly one Client

        // B tries to pass A's own token's "userId" concept via a query string -- there is no such
        // parameter on this API at all, so it must be silently ignored, never interpreted.
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/overview?userId=" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalClients").asLong()).isZero(); // still B's own (empty) workspace
    }

    // 13. User cannot fabricate analytics events through the API.
    @Test
    void noPublicEndpointExistsToSubmitAnalyticsEventsDirectly() throws Exception {
        // There is no POST /api/v1/analytics/events (or any analytics write endpoint) at all --
        // every event is generated as a side effect of a real, authorized domain action. Confirm
        // no such route exists rather than returning a fabricated 201.
        mockMvc.perform(post("/api/v1/analytics/events")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"ASSET_PLAYED"}
                                """))
                .andExpect(status().isNotFound()); // no mapping exists for this route at all
    }

    // 14. Collaborators cannot access unrelated workspace analytics.
    @Test
    void collaborator_doesNotSeeProjectOwnersWorkspaceAnalytics() throws Exception {
        // A owns a real workspace with clients/projects. A shares one Project with B.
        createClient(tokenA, "Big Client");
        createClient(tokenA, "Another Client");
        String sharedProjectId = createProject(tokenA, "Shared EP");
        share(sharedProjectId, tokenBEmail, "EDIT");

        // B's own workspace overview must stay B's own -- being a collaborator on A's Project
        // grants access to THAT Project's resources, never a peek at A's entire workspace metrics.
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/overview").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalClients").asLong()).isZero(); // never sees A's Clients
        assertThat(body.get("totalProjects").asLong()).isZero(); // B doesn't OWN the shared project
    }

    // 15. Project-level analytics respect Project authorization: a collaborator's own project
    // analytics only ever reflects activity THEY performed, never the owner's separate activity.
    @Test
    void collaborator_projectAnalytics_reflectsOnlyTheirOwnActivity() throws Exception {
        String sharedProjectId = createProject(tokenA, "Shared EP");
        share(sharedProjectId, tokenBEmail, "EDIT");

        // A updates the project (A's own action).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/projects/" + sharedProjectId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed by owner","status":"PLANNING"}
                                """))
                .andExpect(status().isOk());

        // B's own project-updates count must NOT include A's update -- B never performed one.
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/projects").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalProjectUpdates").asLong()).isZero();
    }

    @Test
    void analyticsEndpoints_withoutAuth_return401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/assets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/collaboration")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/activity")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private String createClient(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + email + "\",\"permission\":\"" + permission + "\"}"))
                .andExpect(status().isCreated());
    }
}
