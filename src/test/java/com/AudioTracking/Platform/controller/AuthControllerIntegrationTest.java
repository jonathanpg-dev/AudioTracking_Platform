package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    // Replaces the real Google verifier bean so these tests never make a network call to
    // Google — each test controls exactly what a "verified" token looks like.
    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private void mockGoogleToken(String subject, String email, boolean emailVerified) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(idToken);
    }

    // --- Register ---

    @Test
    void register_returnsCreatedUser_withNoPasswordFieldExposed() throws Exception {
        long suffix = System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"regUser%d","email":"regUser%d@example.com","password":"password123"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        long suffix = System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"dupUser%d","email":"dupA%d@example.com","password":"password123"}
                        """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"dupUser%d","email":"dupB%d@example.com","password":"password123"}
                        """.formatted(suffix, suffix)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        long suffix = System.nanoTime();
        String email = "dupEmail" + suffix + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"emailUserA%d","email":"%s","password":"password123"}
                        """.formatted(suffix, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"emailUserB%d","email":"%s","password":"password123"}
                        """.formatted(suffix, email)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidFields_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // --- Login ---

    @Test
    void login_correctCredentials_returnsToken() throws Exception {
        long suffix = System.nanoTime();
        String username = "loginUser" + suffix;
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@example.com","password":"password123"}
                        """.formatted(username, username)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","password":"password123"}
                        """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_wrongPassword_returns401_genericMessage() throws Exception {
        long suffix = System.nanoTime();
        String username = "wrongPass" + suffix;
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@example.com","password":"password123"}
                        """.formatted(username, username)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","password":"wrongpassword"}
                        """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_nonexistentUser_returns401_sameMessageAsWrongPassword() throws Exception {
        // Deliberately asserts the identical message as the wrong-password case above —
        // that's what prevents username enumeration via the login endpoint.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"nobodyAtAllxyz","password":"password123"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    // --- Google login ---

    @Test
    void googleLogin_newVerifiedEmail_createsAccountAndReturnsToken() throws Exception {
        mockGoogleToken("google-sub-" + System.nanoTime(), "newgoogle" + System.nanoTime() + "@example.com", true);

        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":"whatever-since-verifier-is-mocked"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void googleLogin_linksToExistingPasswordAccount_withoutBreakingPasswordLogin() throws Exception {
        long suffix = System.nanoTime();
        String email = "linkme" + suffix + "@example.com";
        String username = "linkUser" + suffix;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123"}
                        """.formatted(username, email)))
                .andExpect(status().isCreated());

        mockGoogleToken("google-sub-link-" + suffix, email, true);

        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":"whatever"}
                        """))
                .andExpect(status().isOk());

        // The original password login must still work after linking.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","password":"password123"}
                        """.formatted(username)))
                .andExpect(status().isOk());
    }

    @Test
    void googleLogin_unverifiedEmail_returns401() throws Exception {
        mockGoogleToken("google-sub-" + System.nanoTime(), "unverified" + System.nanoTime() + "@example.com", false);

        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":"whatever"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLogin_verifierRejectsToken_returns401NotServerError() throws Exception {
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":"garbage"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLogin_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.idToken").exists());
    }
}
