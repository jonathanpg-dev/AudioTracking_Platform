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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollectionControllerIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("collOwnerA" + suffix, "collOwnerA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("collOwnerB" + suffix, "collOwnerB" + suffix + "@example.com", "password123");
    }

    private String createCollectionAndGetId(String token, String name) throws Exception {
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
    void createCollection_returnsCreatedCollection_withEmptyAssetList() throws Exception {
        mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Favorites\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Favorites"))
                .andExpect(jsonPath("$.assetIds").isArray())
                .andExpect(jsonPath("$.assetIds").isEmpty());
    }

    @Test
    void getCollectionById_asOwner_returns200() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(get("/api/v1/collections/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Favorites"));
    }

    // --- sort ---

    @Test
    void getCollections_sortByCreatedAtAscending_returnsOldestFirst() throws Exception {
        createCollectionAndGetId(tokenA, "First");
        createCollectionAndGetId(tokenA, "Second");

        mockMvc.perform(get("/api/v1/collections?sortBy=createdAt&sortDir=asc").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[1].name").value("Second"));
    }

    @Test
    void getCollections_sortByUpdatedAtDescending_mostRecentlyModifiedFirst() throws Exception {
        String firstId = createCollectionAndGetId(tokenA, "First");
        createCollectionAndGetId(tokenA, "Second");

        // Renaming is the only mutation a Collection has (see Collection.java) -- touch "First"
        // after both were created so it becomes the more recently *modified* one despite being
        // created first.
        mockMvc.perform(put("/api/v1/collections/" + firstId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First (renamed)\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/collections?sortBy=updatedAt&sortDir=desc").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("First (renamed)"))
                .andExpect(jsonPath("$[1].name").value("Second"));
    }

    @Test
    void getCollections_sortByUnrecognizedOrMaliciousField_fallsBackSafely_doesNotError() throws Exception {
        createCollectionAndGetId(tokenA, "First");
        createCollectionAndGetId(tokenA, "Second");

        mockMvc.perform(get("/api/v1/collections?sortBy=user.passwordHash").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/collections?sortBy=" + java.net.URLEncoder.encode("createdAt; DROP TABLE collection; --", "UTF-8"))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getCollections_asOwner_containsCreatedCollection() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(get("/api/v1/collections").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());
    }

    @Test
    void updateCollection_asOwner_renamesIt() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(put("/api/v1/collections/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void deleteCollection_asOwner_returns204ThenCollectionIsGone() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");

        mockMvc.perform(delete("/api/v1/collections/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/collections/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- Ownership boundary ---

    @Test
    void getCollections_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/collections")).andExpect(status().isUnauthorized());
    }

    @Test
    void getCollectionById_asDifferentUser_returns404() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(get("/api/v1/collections/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCollections_asDifferentUser_returnsEmptyList() throws Exception {
        createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(get("/api/v1/collections").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateCollection_asDifferentUser_returns404() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(put("/api/v1/collections/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCollection_asDifferentUser_returns404_collectionSurvives() throws Exception {
        String id = createCollectionAndGetId(tokenA, "Favorites");

        mockMvc.perform(delete("/api/v1/collections/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/collections/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // --- Same name allowed across different users (no uniqueness constraint, unlike Tag) ---

    @Test
    void createCollection_sameNameAsAnotherUsersCollection_isAllowed() throws Exception {
        createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Favorites\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createCollection_sameNameTwiceForSameUser_isAllowed_noDuplicateCheck() throws Exception {
        createCollectionAndGetId(tokenA, "Favorites");
        mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Favorites\"}"))
                .andExpect(status().isCreated());
    }

    // --- Validation / not-found ---

    @Test
    void createCollection_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createCollection_overlongName_returns400() throws Exception {
        String longName = "x".repeat(151);
        mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void getCollectionById_nonexistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/collections/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCollectionById_malformedUuid_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/collections/not-a-uuid").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }
}
