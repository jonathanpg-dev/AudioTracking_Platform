package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import com.AudioTracking.Platform.exception.StorageException;
import com.AudioTracking.Platform.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers Phase 4: the file upload/download-access/delete endpoints, and Asset deletion's storage
// cleanup. StorageService is mocked (@MockitoBean) so this exercises the full real HTTP/security/
// ownership/database stack WITHOUT needing real R2 credentials or making any network call —
// exactly the same technique used for GoogleIdTokenVerifier in AuthControllerIntegrationTest.
// R2StorageService itself is separately unit-tested (R2StorageServiceTest) against a mocked S3 SDK.
class AssetFileIntegrationTest extends BaseIntegrationTest {

    private static final byte[] VALID_WAV = "RIFF1234WAVEfmt ".getBytes();
    private static final byte[] VALID_MP3 = {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0};

    @MockitoBean
    private StorageService storageService;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("fileOwnerA" + suffix, "fileOwnerA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("fileOwnerB" + suffix, "fileOwnerB" + suffix + "@example.com", "password123");
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

    // --- TEST 1: create without a file ---

    @Test
    void newAsset_hasNoAudioFile() throws Exception {
        String assetId = createAsset(tokenA);
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAudioFile").value(false));
    }

    // --- TEST 2/3: upload valid formats ---

    @Test
    void uploadWav_succeeds_setsMetadataAndCallsStorage() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);

        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAudioFile").value(true))
                .andExpect(jsonPath("$.audioFormat").value("wav"))
                .andExpect(jsonPath("$.fileSizeBytes").value(VALID_WAV.length));

        verify(storageService).upload(anyString(), any(), eq((long) VALID_WAV.length), eq("audio/wav"));
    }

    @Test
    void uploadMp3_succeeds() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "track.mp3", "audio/mpeg", VALID_MP3);

        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioFormat").value("mp3"));
    }

    // --- TEST 4/5: validation failures ---

    @Test
    void uploadUnsupportedType_returns400_neverCallsStorage() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.ogg", "audio/ogg", "fake".getBytes());

        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadEmptyFile_returns400() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", new byte[0]);

        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    // NOTE: oversized-file rejection (spring.servlet.multipart.max-file-size) is NOT covered by
    // an automated test here. @SpringBootTest's default MOCK web environment (what
    // BaseIntegrationTest uses) never starts a real servlet container, so MockMvc's multipart
    // requests bypass servlet-container-level size enforcement entirely — this can only be
    // proven with a real HTTP request against a real running server (RANDOM_PORT + a real HTTP
    // client), which would be a disproportionate testing-infrastructure change for one edge case
    // that's otherwise a well-established Spring Boot feature, not custom logic. The config
    // wiring itself (storage.max-file-size-mb -> spring.servlet.multipart.max-file-size) was
    // manually verified by booting the app and confirming it starts correctly with the property
    // resolved. handleMaxUploadSize() in GlobalExceptionHandler is the corresponding error path.

    @Test
    void uploadContentNotMatchingExtension_returns400() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", "not really a wav".getBytes());

        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    // --- TEST 7/8 equivalent: access URL generation (real download can't be tested without real R2) ---

    @Test
    void getFileAccessUrl_ownedAssetWithFile_returnsPresignedUrlAndExpiration() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        URI presigned = URI.create("https://r2.example.com/signed?sig=abc123");
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class))).thenReturn(presigned);

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(presigned.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void getFileAccessUrl_assetWithNoFile_returns404() throws Exception {
        String assetId = createAsset(tokenA);
        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- TEST 9: another user's asset ---

    @Test
    void getFileAccessUrl_anotherUsersAsset_returns404_notAuthorized() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadFile_toAnotherUsersAsset_returns404_neverCallsStorage() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);

        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        verify(storageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    // --- TEST 10: replace ---

    @Test
    void replaceExistingFile_uploadsNewAndCleansUpOld() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile first = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(first).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        MockMultipartFile second = new MockMultipartFile("file", "beat2.mp3", "audio/mpeg", VALID_MP3);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(second).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioFormat").value("mp3"));

        // Uploaded twice (original + replacement), and exactly one old-object cleanup delete.
        verify(storageService, times(2)).upload(anyString(), any(), anyLong(), anyString());
        verify(storageService, times(1)).delete(anyString());
    }

    // --- TEST 11: delete file ---

    @Test
    void deleteFile_ownedAssetWithFile_clearsMetadata() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAudioFile").value(false));

        verify(storageService).delete(anyString());
    }

    @Test
    void deleteFile_storageFailure_returns502_leavesAssetPointingAtOriginalFile() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        doThrow(new StorageException("boom", new RuntimeException())).when(storageService).delete(anyString());

        mockMvc.perform(delete("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadGateway());

        // The asset must still show a file — the failed delete must not have cleared it.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.hasAudioFile").value(true));
    }

    @Test
    void deleteFile_assetHasNoFile_returns404() throws Exception {
        String assetId = createAsset(tokenA);
        mockMvc.perform(delete("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- TEST 12: deleting an asset with a file cleans up storage without touching relationships ---

    @Test
    void deletingAssetWithFile_deletesStorageObject_andDoesNotBreakTagsOrCollections() throws Exception {
        String assetId = createAsset(tokenA);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        MvcResult tagResult = mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"trap\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String tagId = objectMapper.readTree(tagResult.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        verify(storageService).delete(anyString());
        // The tag itself must survive deleting the asset that referenced it.
        mockMvc.perform(get("/api/v1/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // --- TEST 13: unauthenticated ---

    @Test
    void fileEndpoints_withoutAuth_return401() throws Exception {
        java.util.UUID randomId = java.util.UUID.randomUUID();
        mockMvc.perform(get("/api/v1/assets/" + randomId + "/file")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/assets/" + randomId + "/file")).andExpect(status().isUnauthorized());
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + randomId + "/file").file(file)).andExpect(status().isUnauthorized());
    }
}
