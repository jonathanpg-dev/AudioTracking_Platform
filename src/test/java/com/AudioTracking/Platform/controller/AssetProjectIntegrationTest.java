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

// Covers Phase 3 STEP 6: the Asset<->Project optional one-to-many relationship specifically.
// CRUD for Asset and Project individually is already covered by their own controller tests.
class AssetProjectIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("assetProjA" + suffix, "assetProjA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("assetProjB" + suffix, "assetProjB" + suffix + "@example.com", "password123");
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

    private String createAssetWithProject(String token, String projectId) throws Exception {
        String body = projectId == null
                ? "{\"title\":\"Beat A\",\"assetType\":\"BEAT\"}"
                : "{\"title\":\"Beat A\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    // --- Happy path ---

    @Test
    void createAsset_withOwnedProjectId_returnsAssetWithProjectPopulated() throws Exception {
        String projectId = createProject(tokenA, "R&B EP");

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beat A\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectName").value("R&B EP"));
    }

    @Test
    void createAsset_withNoProjectId_leavesProjectFieldsNull() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Standalone Beat","assetType":"BEAT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.projectName").doesNotExist());
    }

    @Test
    void getAsset_reflectsAssignedProject() throws Exception {
        String projectId = createProject(tokenA, "R&B EP");
        String createBody = createAssetWithProject(tokenA, projectId);
        String assetId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @Test
    void updateAsset_assigningProject_setsIt() throws Exception {
        String projectId = createProject(tokenA, "R&B EP");
        String createBody = createAssetWithProject(tokenA, null); // starts unassigned
        String assetId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beat A\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @Test
    void updateAsset_omittingProjectId_unassignsIt() throws Exception {
        String projectId = createProject(tokenA, "R&B EP");
        String createBody = createAssetWithProject(tokenA, projectId);
        String assetId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Beat A","assetType":"BEAT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").doesNotExist());
    }

    // --- Ownership: cannot assign into another user's project ---

    @Test
    void createAsset_withAnotherUsersProjectId_returns404_notCreated() throws Exception {
        String otherUsersProjectId = createProject(tokenB, "Secret Project");

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beat A\",\"assetType\":\"BEAT\",\"projectId\":\"" + otherUsersProjectId + "\"}"))
                .andExpect(status().isNotFound());

        // Confirm nothing got created at all.
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateAsset_reassigningToAnotherUsersProject_returns404() throws Exception {
        String createBody = createAssetWithProject(tokenA, null);
        String assetId = objectMapper.readTree(createBody).get("id").asText();
        String otherUsersProjectId = createProject(tokenB, "Secret Project");

        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beat A\",\"assetType\":\"BEAT\",\"projectId\":\"" + otherUsersProjectId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // --- Delete behavior: the actual point of this whole feature ---

    @Test
    void deletingProject_unassignsAssets_withoutDeletingThem() throws Exception {
        String projectId = createProject(tokenA, "R&B EP");
        String createBody = createAssetWithProject(tokenA, projectId);
        String assetId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // The asset must survive, just without a project anymore.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Beat A"))
                .andExpect(jsonPath("$.projectId").doesNotExist());
    }

    @Test
    void deletingAsset_doesNotAffectItsProject() throws Exception {
        String projectId = createProject(tokenA, "R&B EP");
        String createBody = createAssetWithProject(tokenA, projectId);
        String assetId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // --- Malformed input ---

    @Test
    void createAsset_malformedProjectIdInBody_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Beat A","assetType":"BEAT","projectId":"not-a-uuid"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
