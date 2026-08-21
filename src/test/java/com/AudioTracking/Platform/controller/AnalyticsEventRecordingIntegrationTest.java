package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 6, "ANALYTICS EVENT TESTS": every successful action that should generate an
// AnalyticsEvent, verified end to end -- perform the real action through its real endpoint, then
// read the corresponding analytics endpoint and confirm the count actually moved. This proves the
// real service-layer wiring, not just that AnalyticsService.record() works in isolation (that's
// AnalyticsServiceImplTest's job).
class AnalyticsEventRecordingIntegrationTest extends BaseIntegrationTest {

    private static final byte[] VALID_WAV = "RIFF1234WAVEfmt ".getBytes();

    @MockitoBean
    private StorageService storageService;

    private String tokenA;
    private String tokenB;
    private String tokenBEmail;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("analyticsEvtA" + suffix, "analyticsEvtA" + suffix + "@example.com", "password123");
        tokenBEmail = "analyticsEvtB" + suffix + "@example.com";
        tokenB = registerAndLogin("analyticsEvtB" + suffix, tokenBEmail, "password123");
    }

    private String createAsset(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Beat","assetType":"BEAT"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private long assetTotal(String token, String field) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asLong();
    }

    // 1. Successful Asset upload records ASSET_UPLOADED.
    @Test
    void assetUpload_recordsAssetUploaded() throws Exception {
        String assetId = createAsset(tokenA);
        long before = assetTotal(tokenA, "totalUploads");

        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        assertEquals(before + 1, assetTotal(tokenA, "totalUploads"));
    }

    @Test
    void assetUpload_unauthorized_doesNotRecordEvent() throws Exception {
        // User B has no access to User A's asset at all.
        String assetId = createAsset(tokenA);
        long before = assetTotal(tokenB, "totalUploads");

        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        assertEquals(before, assetTotal(tokenB, "totalUploads"));
    }

    // 2. Successful Asset play records ASSET_PLAYED.
    @Test
    void assetPlay_recordsAssetPlayed() throws Exception {
        String assetId = uploadedAsset(tokenA);
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://r2.example.com/signed"));
        long before = assetTotal(tokenA, "totalPlays");

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        assertEquals(before + 1, assetTotal(tokenA, "totalPlays"));
    }

    // 3. Successful Asset download records ASSET_DOWNLOADED.
    @Test
    void assetDownload_recordsAssetDownloaded_notAssetPlayed() throws Exception {
        String assetId = uploadedAsset(tokenA);
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://r2.example.com/signed"));
        long playsBefore = assetTotal(tokenA, "totalPlays");
        long downloadsBefore = assetTotal(tokenA, "totalDownloads");

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file?download=true").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        assertEquals(downloadsBefore + 1, assetTotal(tokenA, "totalDownloads"));
        assertEquals(playsBefore, assetTotal(tokenA, "totalPlays")); // unchanged
    }

    @Test
    void assetDownload_unauthorized_doesNotRecordEvent() throws Exception {
        String assetId = uploadedAsset(tokenA);
        long before = assetTotal(tokenB, "totalDownloads");

        mockMvc.perform(get("/api/v1/assets/" + assetId + "/file?download=true").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        assertEquals(before, assetTotal(tokenB, "totalDownloads"));
    }

    // 4. Successful Asset deletion records ASSET_DELETED.
    @Test
    void assetDelete_recordsAssetDeleted() throws Exception {
        String assetId = createAsset(tokenA);
        long before = assetTotal(tokenA, "totalDeletions");

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertEquals(before + 1, assetTotal(tokenA, "totalDeletions"));
    }

    @Test
    void assetDelete_unauthorized_doesNotRecordEvent() throws Exception {
        String assetId = createAsset(tokenA);
        long before = assetTotal(tokenB, "totalDeletions");

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        assertEquals(before, assetTotal(tokenB, "totalDeletions"));
        // Confirm it really wasn't deleted either.
        mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // 5. Successful Project creation records PROJECT_CREATED.
    @Test
    void projectCreate_recordsProjectCreated() throws Exception {
        long before = projectTotal(tokenA, "totalProjects");

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New EP"}
                                """))
                .andExpect(status().isCreated());

        assertEquals(before + 1, projectTotal(tokenA, "totalProjects"));
    }

    // 6. Successful Project update records PROJECT_UPDATED.
    @Test
    void projectUpdate_recordsProjectUpdated() throws Exception {
        String projectId = createProject(tokenA);
        long before = projectTotal(tokenA, "totalProjectUpdates");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed EP","status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());

        assertEquals(before + 1, projectTotal(tokenA, "totalProjectUpdates"));
    }

    @Test
    void projectUpdate_byNonOwner_doesNotRecordEvent() throws Exception {
        String projectId = createProject(tokenA);
        long before = projectTotal(tokenA, "totalProjectUpdates");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked","status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isNotFound());

        assertEquals(before, projectTotal(tokenA, "totalProjectUpdates"));
    }

    // 7. Successful Project sharing records PROJECT_SHARED.
    @Test
    void projectShare_recordsProjectShared() throws Exception {
        String projectId = createProject(tokenA);
        long before = collabTotal(tokenA, "totalSharesCreated");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + tokenBEmail + "\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isCreated());

        assertEquals(before + 1, collabTotal(tokenA, "totalSharesCreated"));
    }

    // 8. Successful Collection creation records COLLECTION_CREATED.
    @Test
    void collectionCreate_recordsCollectionCreated() throws Exception {
        long before = overviewTotal(tokenA, "totalCollections");

        mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Favorites"}
                                """))
                .andExpect(status().isCreated());

        assertEquals(before + 1, overviewTotal(tokenA, "totalCollections"));
    }

    // 9. Successful Client creation records CLIENT_CREATED.
    @Test
    void clientCreate_recordsClientCreated() throws Exception {
        long before = overviewTotal(tokenA, "totalClients");

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"John Smith"}
                                """))
                .andExpect(status().isCreated());

        assertEquals(before + 1, overviewTotal(tokenA, "totalClients"));
    }

    // --- helpers ---

    private String uploadedAsset(String token) throws Exception {
        String assetId = createAsset(token);
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return assetId;
    }

    private String createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"EP"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private long projectTotal(String token, String field) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asLong();
    }

    private long collabTotal(String token, String field) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/collaboration").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asLong();
    }

    private long overviewTotal(String token, String field) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asLong();
    }

    private void assertEquals(long expected, long actual) {
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
    }
}
