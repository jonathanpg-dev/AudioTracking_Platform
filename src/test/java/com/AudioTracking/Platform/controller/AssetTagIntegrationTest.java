package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers Phase 3 STEP 4: the Asset<->Tag many-to-many relationship specifically.
// CRUD for Asset and Tag individually is already covered by their own controller tests.
class AssetTagIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("assetTagA" + suffix, "assetTagA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("assetTagB" + suffix, "assetTagB" + suffix + "@example.com", "password123");
    }

    private String createAsset(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dark Trap Loop","assetType":"BEAT"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTag(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- Happy path ---

    @Test
    void addTag_attachesItAndReturnsUpdatedAsset() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap");

        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.tags[0].id").value(tagId))
                .andExpect(jsonPath("$.tags[0].name").value("trap"));
    }

    @Test
    void getAsset_reflectsAttachedTags() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap");
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.tags[0].name").value("trap"));
    }

    @Test
    void addTag_calledTwice_isIdempotent_noDuplicateInList() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap");

        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(1)));
    }

    @Test
    void removeTag_attached_detachesIt() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap");
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void removeTag_notCurrentlyAttached_isANoOp_notAnError() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap"); // created but never attached

        mockMvc.perform(delete("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    // --- Ownership: cannot cross-pollinate another user's assets/tags ---

    @Test
    void addTag_ownAssetWithAnotherUsersTag_returns404_notAssociated() throws Exception {
        String assetId = createAsset(tokenA);
        String otherUsersTagId = createTag(tokenB, "trap");

        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + otherUsersTagId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void addTag_anotherUsersAsset_returns404() throws Exception {
        String otherUsersAssetId = createAsset(tokenB);
        String tagId = createTag(tokenA, "trap");

        mockMvc.perform(post("/api/v1/assets/" + otherUsersAssetId + "/tags/" + tagId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeTag_anotherUsersAsset_returns404() throws Exception {
        String otherUsersAssetId = createAsset(tokenB);
        mockMvc.perform(delete("/api/v1/assets/" + otherUsersAssetId + "/tags/" + UUID_PLACEHOLDER)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    private static final String UUID_PLACEHOLDER = "00000000-0000-0000-0000-000000000000";

    // --- Delete behavior: association cleanup must not delete the other side ---

    @Test
    void deletingAsset_removesAssociation_butTagSurvives() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap");
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // The tag must still exist — deleting the asset must not cascade into deleting the tag.
        mockMvc.perform(get("/api/v1/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void deletingTag_removesAssociation_butAssetSurvivesWithoutIt() throws Exception {
        String assetId = createAsset(tokenA);
        String tagId = createTag(tokenA, "trap");
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // This is the case that would fail with a raw foreign-key violation if the join-table
        // cleanup query didn't run before the tag row itself is deleted.
        mockMvc.perform(delete("/api/v1/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    // --- Malformed input ---

    @Test
    void addTag_malformedTagIdInUrl_returns400NotServerError() throws Exception {
        String assetId = createAsset(tokenA);
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/not-a-uuid")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }
}
