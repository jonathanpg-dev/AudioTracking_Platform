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

// Covers the Collection<->Asset many-to-many relationship specifically (the item the user added
// to their own Phase 3 spec). CRUD for Collection and Asset individually is already covered by
// their own controller tests.
class CollectionAssetIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("collAssetA" + suffix, "collAssetA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("collAssetB" + suffix, "collAssetB" + suffix + "@example.com", "password123");
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

    private String createCollection(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- Happy path ---

    @Test
    void addAsset_attachesItAndReturnsUpdatedCollection() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA);

        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(1)))
                .andExpect(jsonPath("$.assetIds[0]").value(assetId));
    }

    @Test
    void getCollection_reflectsAttachedAssets() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA);
        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/collections/" + collectionId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(1)))
                .andExpect(jsonPath("$.assetIds[0]").value(assetId));
    }

    @Test
    void anAssetCanBelongToMultipleCollections() throws Exception {
        String assetId = createAsset(tokenA);
        String collectionA = createCollection(tokenA, "Favorites");
        String collectionB = createCollection(tokenA, "Summer Playlist");

        mockMvc.perform(post("/api/v1/collections/" + collectionA + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/collections/" + collectionB + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(1)));

        mockMvc.perform(get("/api/v1/collections/" + collectionA).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.assetIds", hasSize(1)));
    }

    @Test
    void addAsset_calledTwice_isIdempotent_noDuplicateInList() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA);

        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(1)));
    }

    @Test
    void removeAsset_attached_detachesIt() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA);
        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(0)));
    }

    @Test
    void removeAsset_notCurrentlyAttached_isANoOp_notAnError() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA); // created but never added

        mockMvc.perform(delete("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(0)));
    }

    // --- Ownership: cannot cross-pollinate another user's assets/collections ---

    @Test
    void addAsset_ownCollectionWithAnotherUsersAsset_returns404_notAssociated() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String otherUsersAssetId = createAsset(tokenB);

        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + otherUsersAssetId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/collections/" + collectionId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.assetIds", hasSize(0)));
    }

    @Test
    void addAsset_anotherUsersCollection_returns404() throws Exception {
        String otherUsersCollectionId = createCollection(tokenB, "Secret Collection");
        String assetId = createAsset(tokenA);

        mockMvc.perform(post("/api/v1/collections/" + otherUsersCollectionId + "/assets/" + assetId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- Delete behavior: association cleanup must not delete the other side, in EITHER direction ---

    @Test
    void deletingCollection_removesAssociation_butAssetSurvives() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA);
        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/collections/" + collectionId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // The asset must still exist — deleting the collection must not cascade into deleting it.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAsset_removesAssociation_butCollectionSurvivesWithoutIt() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        String assetId = createAsset(tokenA);
        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // The reverse direction from Asset<->Tag: Collection owns this relationship, so Asset is
        // the side that needs explicit cleanup. This is the case that would fail if that cleanup
        // were missing or wrong (same class of bug as the Asset<->Tag one, opposite direction).
        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/collections/" + collectionId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds", hasSize(0)));
    }

    // --- Malformed input ---

    @Test
    void addAsset_malformedAssetIdInUrl_returns400NotServerError() throws Exception {
        String collectionId = createCollection(tokenA, "Favorites");
        mockMvc.perform(post("/api/v1/collections/" + collectionId + "/assets/not-a-uuid")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }
}
