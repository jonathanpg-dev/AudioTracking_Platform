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

// Phase 5: Client CRUD and ownership. Client is deliberately its own owned resource, not
// shareable/collaborative at all -- only Project sharing is (see ProjectCollaborationIntegrationTest).
class ClientControllerIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("clientOwnerA" + suffix, "clientOwnerA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("clientOwnerB" + suffix, "clientOwnerB" + suffix + "@example.com", "password123");
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

    // 1. User can create Client.
    @Test
    void createClient_returnsCreatedClient() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"John Smith","email":"john@example.com","company":"Smith Records","notes":"Prefers trap beats"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Smith"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.company").value("Smith Records"));
    }

    @Test
    void createClient_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createClient_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"John Smith","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createClient_ignoresAnyOwnerFieldInBody_ownerIsAlwaysTheAuthenticatedUser() throws Exception {
        // CreateClientRequest has no owner/userId field at all, so there's nothing for extra
        // JSON properties to bind to -- but confirm the client only shows up for the real caller.
        createClient(tokenA, "John Smith");

        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // 2. User can retrieve their Clients.
    @Test
    void getClients_returnsOnlyCallersClients() throws Exception {
        createClient(tokenA, "Client A1");
        createClient(tokenA, "Client A2");
        createClient(tokenB, "Client B1");

        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // 3. User can retrieve one of their Clients.
    @Test
    void getClient_ownedByCaller_returnsIt() throws Exception {
        String clientId = createClient(tokenA, "John Smith");

        mockMvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Smith"));
    }

    // 6. User cannot access another user's Client.
    @Test
    void getClient_notOwnedByCaller_returns404() throws Exception {
        String clientId = createClient(tokenA, "Secret Client");

        mockMvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClient_nonexistentId_returns404_sameAsUnowned() throws Exception {
        mockMvc.perform(get("/api/v1/clients/" + java.util.UUID.randomUUID()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // 4. User can update their Client.
    @Test
    void updateClient_ownedByCaller_updatesIt() throws Exception {
        String clientId = createClient(tokenA, "John Smith");

        mockMvc.perform(put("/api/v1/clients/" + clientId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"John Smith Jr.","email":"johnjr@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Smith Jr."))
                .andExpect(jsonPath("$.email").value("johnjr@example.com"));
    }

    // 7. User cannot update another user's Client.
    @Test
    void updateClient_notOwnedByCaller_returns404_andDoesNotChangeIt() throws Exception {
        String clientId = createClient(tokenA, "John Smith");

        mockMvc.perform(put("/api/v1/clients/" + clientId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked Name"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.name").value("John Smith"));
    }

    // 5. User can delete their Client.
    @Test
    void deleteClient_ownedByCaller_deletesIt() throws Exception {
        String clientId = createClient(tokenA, "John Smith");

        mockMvc.perform(delete("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // 8. User cannot delete another user's Client.
    @Test
    void deleteClient_notOwnedByCaller_returns404_andDoesNotDeleteIt() throws Exception {
        String clientId = createClient(tokenA, "John Smith");

        mockMvc.perform(delete("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void clientEndpoints_withoutAuth_return401() throws Exception {
        java.util.UUID randomId = java.util.UUID.randomUUID();
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clients/" + randomId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
