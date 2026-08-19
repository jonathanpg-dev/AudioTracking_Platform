package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetControllerIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("assetOwnerA" + suffix, "assetOwnerA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("assetOwnerB" + suffix, "assetOwnerB" + suffix + "@example.com", "password123");
    }

    private String createAssetAndGetId(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dark Trap Loop","assetType":"BEAT","bpm":140,"musicalKey":"Cm","durationSeconds":32,"fileSizeBytes":4200000,"audioFormat":"wav"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- Happy path ---

    @Test
    void createAsset_returnsCreatedAsset_withoutExposingOwner() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dark Trap Loop","assetType":"BEAT","bpm":140,"musicalKey":"Cm","durationSeconds":32,"fileSizeBytes":4200000,"audioFormat":"wav"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Dark Trap Loop"))
                .andExpect(jsonPath("$.assetType").value("BEAT"))
                .andExpect(jsonPath("$.bpm").value(140))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void getAssetById_asOwner_returns200() throws Exception {
        String id = createAssetAndGetId(tokenA);
        mockMvc.perform(get("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getAssets_asOwner_containsCreatedAsset() throws Exception {
        String id = createAssetAndGetId(tokenA);
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());
    }

    @Test
    void updateAsset_asOwner_updatesFieldsAndReturns200() throws Exception {
        String id = createAssetAndGetId(tokenA);
        mockMvc.perform(put("/api/v1/assets/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dark Trap Loop v2","assetType":"BEAT","bpm":142,"musicalKey":"C#m","durationSeconds":34,"fileSizeBytes":4300000,"audioFormat":"wav"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dark Trap Loop v2"))
                .andExpect(jsonPath("$.bpm").value(142));
    }

    @Test
    void deleteAsset_asOwner_returns204ThenAssetIsGone() throws Exception {
        String id = createAssetAndGetId(tokenA);

        mockMvc.perform(delete("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- Ownership boundary (the security-critical part of this feature) ---

    @Test
    void getAssets_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/assets")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAssetById_asDifferentUser_returns404_sameAsNonexistentId() throws Exception {
        String id = createAssetAndGetId(tokenA);
        mockMvc.perform(get("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAssets_asDifferentUser_returnsEmptyList_noCrossContamination() throws Exception {
        createAssetAndGetId(tokenA);
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateAsset_asDifferentUser_returns404() throws Exception {
        String id = createAssetAndGetId(tokenA);
        mockMvc.perform(put("/api/v1/assets/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","assetType":"BEAT"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAsset_asDifferentUser_returns404_assetSurvives() throws Exception {
        String id = createAssetAndGetId(tokenA);

        mockMvc.perform(delete("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // --- Validation ---

    @Test
    void createAsset_blankTitle_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","assetType":"BEAT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void createAsset_missingAssetType_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No Type"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.assetType").exists());
    }

    @Test
    void createAsset_invalidEnumValue_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad Enum","assetType":"GARBAGE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAsset_lowercaseEnumValue_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad Case","assetType":"beat"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAsset_bpmAboveMax_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Too Fast","assetType":"BEAT","bpm":9999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bpm").exists());
    }

    @Test
    void createAsset_bpmBelowMin_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Too Slow","assetType":"BEAT","bpm":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bpm").exists());
    }

    @Test
    void createAsset_negativeDuration_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Negative","assetType":"BEAT","durationSeconds":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationSeconds").exists());
    }

    @Test
    void createAsset_negativeFileSize_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Negative","assetType":"BEAT","fileSizeBytes":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fileSizeBytes").exists());
    }

    @Test
    void createAsset_overlongTitle_returns400() throws Exception {
        String longTitle = "x".repeat(201);
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void createAsset_musicalKeyExactly30Chars_isAccepted_boundaryNotOffByOne() throws Exception {
        String key30 = "x".repeat(30);
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Boundary\",\"assetType\":\"BEAT\",\"musicalKey\":\"" + key30 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.musicalKey").value(key30));
    }

    @Test
    void createAsset_musicalKeyOver30Chars_returns400() throws Exception {
        String key31 = "x".repeat(31);
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Boundary\",\"assetType\":\"BEAT\",\"musicalKey\":\"" + key31 + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.musicalKey").exists());
    }

    // --- Not-found / malformed input ---

    @Test
    void getAssetById_nonexistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/assets/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAssetById_malformedUuid_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/assets/not-a-uuid").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAssetById_malformedUuidWithoutAuth_returns401_securityRunsBeforeArgumentBinding() throws Exception {
        mockMvc.perform(get("/api/v1/assets/not-a-uuid")).andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedMethod_returns405() throws Exception {
        String id = createAssetAndGetId(tokenA);
        mockMvc.perform(patch("/api/v1/assets/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isMethodNotAllowed());
    }
}
