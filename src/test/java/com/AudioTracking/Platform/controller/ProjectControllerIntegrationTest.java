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

class ProjectControllerIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("projectOwnerA" + suffix, "projectOwnerA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("projectOwnerB" + suffix, "projectOwnerB" + suffix + "@example.com", "password123");
    }

    private String createProjectAndGetId(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- Happy path ---

    @Test
    void createProject_startsAtPlanning_evenIfClientSendsAStatus() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"R&B EP","description":"Summer project","status":"COMPLETED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("R&B EP"))
                .andExpect(jsonPath("$.status").value("PLANNING")); // client-sent status is ignored on create
    }

    @Test
    void getProjectById_asOwner_returns200() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(get("/api/v1/projects/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Film Score"))
                .andExpect(jsonPath("$.status").value("PLANNING"));
    }

    @Test
    void getProjects_asOwner_containsCreatedProject() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());
    }

    @Test
    void updateProject_movesThroughLifecycle() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(put("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Film Score","description":"Now in progress","status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.description").value("Now in progress"));
    }

    @Test
    void deleteProject_asOwner_returns204ThenProjectIsGone() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");

        mockMvc.perform(delete("/api/v1/projects/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- Ownership boundary ---

    @Test
    void getProjects_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void getProjectById_asDifferentUser_returns404() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(get("/api/v1/projects/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjects_asDifferentUser_returnsEmptyList() throws Exception {
        createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateProject_asDifferentUser_returns404() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(put("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked","status":"ARCHIVED"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProject_asDifferentUser_returns404_projectSurvives() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");

        mockMvc.perform(delete("/api/v1/projects/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // --- Validation / not-found ---

    @Test
    void createProject_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createProject_overlongName_returns400() throws Exception {
        String longName = "x".repeat(151);
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void updateProject_missingStatus_returns400() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(put("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Film Score\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void updateProject_invalidStatusValue_returns400NotServerError() throws Exception {
        String id = createProjectAndGetId(tokenA, "Film Score");
        mockMvc.perform(put("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Film Score","status":"GARBAGE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProjectById_nonexistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectById_malformedUuid_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/projects/not-a-uuid").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }
}
