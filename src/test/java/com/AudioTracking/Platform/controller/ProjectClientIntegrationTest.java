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

// Phase 5: the optional Project<->Client relationship specifically. CRUD for each individually is
// already covered by ProjectControllerIntegrationTest / ClientControllerIntegrationTest.
class ProjectClientIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("projClientA" + suffix, "projClientA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("projClientB" + suffix, "projClientB" + suffix + "@example.com", "password123");
    }

    private String createClient(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createProject(String token, String name, String clientId) throws Exception {
        String body = clientId == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"clientId\":\"" + clientId + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    // 9. User can assign their Client to their Project.
    @Test
    void createProject_withOwnedClientId_returnsProjectWithClientPopulated() throws Exception {
        String clientId = createClient(tokenA, "John Smith");

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client EP\",\"clientId\":\"" + clientId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.clientName").value("John Smith"));
    }

    @Test
    void createProject_withNoClientId_leavesClientFieldsNull() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Personal Project"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientName").doesNotExist());
    }

    // 10. User can change the Project Client.
    @Test
    void updateProject_changingClientId_updatesIt() throws Exception {
        String clientId1 = createClient(tokenA, "John Smith");
        String clientId2 = createClient(tokenA, "Jane Doe");
        String createBody = createProject(tokenA, "Client EP", clientId1);
        String projectId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client EP\",\"status\":\"PLANNING\",\"clientId\":\"" + clientId2 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId2))
                .andExpect(jsonPath("$.clientName").value("Jane Doe"));
    }

    // 11. User can remove the Project Client.
    @Test
    void updateProject_omittingClientId_removesAssociation() throws Exception {
        String clientId = createClient(tokenA, "John Smith");
        String createBody = createProject(tokenA, "Client EP", clientId);
        String projectId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Client EP","status":"PLANNING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").doesNotExist());
    }

    // 12. User cannot assign another user's Client to their Project.
    @Test
    void createProject_withAnotherUsersClientId_returns404_notCreated() throws Exception {
        String otherUsersClientId = createClient(tokenB, "Secret Client");

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Project\",\"clientId\":\"" + otherUsersClientId + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateProject_reassigningToAnotherUsersClient_returns404() throws Exception {
        String createBody = createProject(tokenA, "My Project", null);
        String projectId = objectMapper.readTree(createBody).get("id").asText();
        String otherUsersClientId = createClient(tokenB, "Secret Client");

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Project\",\"status\":\"PLANNING\",\"clientId\":\"" + otherUsersClientId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // 13. Deleting Client does not delete Project.
    @Test
    void deletingClient_doesNotDeleteProject_justUnassignsIt() throws Exception {
        String clientId = createClient(tokenA, "John Smith");
        String createBody = createProject(tokenA, "Client EP", clientId);
        String projectId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(delete("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Client EP"))
                .andExpect(jsonPath("$.clientId").doesNotExist());
    }

    // 14. Deleting Project does not delete the Client.
    @Test
    void deletingProject_doesNotDeleteClient() throws Exception {
        String clientId = createClient(tokenA, "John Smith");
        String createBody = createProject(tokenA, "Client EP", clientId);
        String projectId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void createProject_malformedClientIdInBody_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My Project","clientId":"not-a-uuid"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
