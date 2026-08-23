package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 7: GET /users/me, added so the frontend has a "who am I" endpoint. The rest of
// UserController predates this project's automated-testing convention and is out of scope here.
class UserControllerIntegrationTest extends BaseIntegrationTest {

    private String username;
    private String email;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        username = "meUser" + suffix;
        email = "meUser" + suffix + "@example.com";
        token = registerAndLogin(username, email, "password123");
    }

    @Test
    void getCurrentUser_returnsTheAuthenticatedCallersOwnAccount() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getCurrentUser_differentUsers_getDifferentAccounts() throws Exception {
        String otherToken = registerAndLogin("meUserB" + System.nanoTime(), "meUserB" + System.nanoTime() + "@example.com", "password123");

        String myUsername = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String otherUsername = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + otherToken))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(myUsername).isNotEqualTo(otherUsername);
    }

    @Test
    void getCurrentUser_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void unlockCreatorMode_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/creator-mode")).andExpect(status().isUnauthorized());
    }
}
