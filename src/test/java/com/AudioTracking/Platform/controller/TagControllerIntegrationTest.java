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

class TagControllerIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("tagOwnerA" + suffix, "tagOwnerA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("tagOwnerB" + suffix, "tagOwnerB" + suffix + "@example.com", "password123");
    }

    private String createTagAndGetId(String token, String name) throws Exception {
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
    void createTag_returnsCreatedTag() throws Exception {
        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"trap\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("trap"));
    }

    @Test
    void getTagById_asOwner_returns200() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");
        mockMvc.perform(get("/api/v1/tags/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("trap"));
    }

    @Test
    void getTags_asOwner_returnsAlphabeticallySorted() throws Exception {
        createTagAndGetId(tokenA, "trap");
        createTagAndGetId(tokenA, "cinematic");
        mockMvc.perform(get("/api/v1/tags").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("cinematic"))
                .andExpect(jsonPath("$[1].name").value("trap"));
    }

    @Test
    void updateTag_asOwner_renamesIt() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");
        mockMvc.perform(put("/api/v1/tags/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"dark-trap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dark-trap"));
    }

    @Test
    void deleteTag_asOwner_returns204ThenTagIsGone() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");

        mockMvc.perform(delete("/api/v1/tags/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tags/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- Same tag name allowed across different users, forbidden within one user's account ---

    @Test
    void createTag_duplicateNameForSameUser_returns409() throws Exception {
        createTagAndGetId(tokenA, "trap");
        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"trap\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createTag_sameNameAsDifferentUsersTag_isAllowed() throws Exception {
        createTagAndGetId(tokenA, "trap");
        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"trap\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateTag_renamingToAnotherOwnTagsName_returns409() throws Exception {
        createTagAndGetId(tokenA, "trap");
        String secondId = createTagAndGetId(tokenA, "cinematic");

        mockMvc.perform(put("/api/v1/tags/" + secondId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"trap\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateTag_renamingToItsOwnCurrentName_isAllowed() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");
        mockMvc.perform(put("/api/v1/tags/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"trap\"}"))
                .andExpect(status().isOk());
    }

    // --- Ownership boundary ---

    @Test
    void getTags_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    void getTagById_asDifferentUser_returns404() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");
        mockMvc.perform(get("/api/v1/tags/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTags_asDifferentUser_returnsEmptyList() throws Exception {
        createTagAndGetId(tokenA, "trap");
        mockMvc.perform(get("/api/v1/tags").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateTag_asDifferentUser_returns404() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");
        mockMvc.perform(put("/api/v1/tags/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTag_asDifferentUser_returns404_tagSurvives() throws Exception {
        String id = createTagAndGetId(tokenA, "trap");

        mockMvc.perform(delete("/api/v1/tags/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tags/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // --- Validation / not-found ---

    @Test
    void createTag_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createTag_overlongName_returns400() throws Exception {
        String longName = "x".repeat(51);
        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void getTagById_nonexistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tags/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTagById_malformedUuid_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/tags/not-a-uuid").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }
}
