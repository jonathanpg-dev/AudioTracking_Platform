package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import com.AudioTracking.Platform.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 6, "ANALYTICS AGGREGATION TESTS" + "HISTORICAL ANALYTICS TESTS": realistic data built
// through the real API, then verified against the real aggregate queries (real Postgres, not
// mocks) -- this is what actually proves the SQL/JPQL in AnalyticsEventRepository/AssetRepository/
// ProjectShareRepository is correct, not just that the service classes call the right methods.
class AnalyticsAggregationIntegrationTest extends BaseIntegrationTest {

    private static final byte[] VALID_WAV = "RIFF1234WAVEfmt ".getBytes();

    @MockitoBean
    private StorageService storageService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        token = registerAndLogin("analyticsAggU" + suffix, "analyticsAggU" + suffix + "@example.com", "password123");
        when(storageService.generatePresignedDownloadUrl(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://r2.example.com/signed"));
    }

    // 16-20. Workspace totals are exact for realistic, mixed data.
    @Test
    void overview_totalsAreExactlyRight_forKnownData() throws Exception {
        createAsset("Beat 1");
        createAsset("Beat 2");
        createAsset("Beat 3");
        createProject("EP 1");
        createProject("EP 2");
        createCollection("Favorites");
        createClient("John Smith");
        createClient("Jane Doe");
        createTag("trap");

        JsonNode overview = get("/api/v1/analytics/overview");

        assertThat(overview.get("totalAssets").asLong()).isEqualTo(3);
        assertThat(overview.get("totalProjects").asLong()).isEqualTo(2);
        assertThat(overview.get("totalCollections").asLong()).isEqualTo(1);
        assertThat(overview.get("totalClients").asLong()).isEqualTo(2);
        assertThat(overview.get("totalTags").asLong()).isEqualTo(1);
    }

    @Test
    void overview_totalStorageBytes_sumsActualUploadedFileSizes() throws Exception {
        String asset1 = createAsset("Beat 1");
        String asset2 = createAsset("Beat 2");
        uploadFile(asset1); // VALID_WAV.length bytes
        uploadFile(asset2); // VALID_WAV.length bytes again

        JsonNode overview = get("/api/v1/analytics/overview");

        assertThat(overview.get("totalStorageBytes").asLong()).isEqualTo(2L * VALID_WAV.length);
    }

    // 21-23. Play/download/upload counts are exact.
    @Test
    void assetAnalytics_uploadPlayDownloadCounts_areExact() throws Exception {
        String assetId = createAsset("Beat");
        uploadFile(assetId); // 1 upload

        playAsset(assetId);
        playAsset(assetId); // 2 plays
        downloadAsset(assetId); // 1 download

        JsonNode assetAnalytics = get("/api/v1/analytics/assets");
        assertThat(assetAnalytics.get("totalUploads").asLong()).isEqualTo(1);
        assertThat(assetAnalytics.get("totalPlays").asLong()).isEqualTo(2);
        assertThat(assetAnalytics.get("totalDownloads").asLong()).isEqualTo(1);
    }

    // 24. Most-played Assets are correctly ranked.
    @Test
    void assetAnalytics_topPlayedAssets_rankedByPlayCountDescending() throws Exception {
        String popular = createAsset("Popular Beat");
        String mid = createAsset("Mid Beat");
        String unplayed = createAsset("Unplayed Beat");
        uploadFile(popular);
        uploadFile(mid);
        uploadFile(unplayed);

        playAsset(popular);
        playAsset(popular);
        playAsset(popular); // 3 plays
        playAsset(mid); // 1 play
        // unplayed: 0 plays -- must not appear in the ranking at all

        JsonNode assetAnalytics = get("/api/v1/analytics/assets");
        JsonNode topPlayed = assetAnalytics.get("topPlayedAssets");

        assertThat(topPlayed.size()).isEqualTo(2);
        assertThat(topPlayed.get(0).get("assetId").asText()).isEqualTo(popular);
        assertThat(topPlayed.get(0).get("count").asLong()).isEqualTo(3);
        assertThat(topPlayed.get(0).get("title").asText()).isEqualTo("Popular Beat");
        assertThat(topPlayed.get(1).get("assetId").asText()).isEqualTo(mid);
        assertThat(topPlayed.get(1).get("count").asLong()).isEqualTo(1);
    }

    // 25. Most-downloaded Assets are correctly ranked.
    @Test
    void assetAnalytics_topDownloadedAssets_rankedByDownloadCountDescending() throws Exception {
        String mostDownloaded = createAsset("Beat A");
        String lessDownloaded = createAsset("Beat B");
        uploadFile(mostDownloaded);
        uploadFile(lessDownloaded);

        downloadAsset(mostDownloaded);
        downloadAsset(mostDownloaded);
        downloadAsset(lessDownloaded);

        JsonNode topDownloaded = get("/api/v1/analytics/assets").get("topDownloadedAssets");

        assertThat(topDownloaded.get(0).get("assetId").asText()).isEqualTo(mostDownloaded);
        assertThat(topDownloaded.get(0).get("count").asLong()).isEqualTo(2);
    }

    // 26. Project activity is correctly aggregated.
    @Test
    void projectAnalytics_activityAndAssetCounts_areAccurate() throws Exception {
        String activeProject = createProject("Active EP");
        String quietProject = createProject("Quiet EP");

        String asset1 = createAssetInProject("Beat 1", activeProject);
        String asset2 = createAssetInProject("Beat 2", activeProject);
        uploadFile(asset1);
        uploadFile(asset2);
        playAsset(asset1); // 4 events total tied to activeProject: its own PROJECT_CREATED + 2 uploads + 1 play
        createAssetInProject("Lonely Beat", quietProject); // just quietProject's own PROJECT_CREATED

        JsonNode projectAnalytics = get("/api/v1/analytics/projects");
        JsonNode mostActive = projectAnalytics.get("mostActiveProjects");

        assertThat(mostActive.get(0).get("projectId").asText()).isEqualTo(activeProject);
        assertThat(mostActive.get(0).get("eventCount").asLong()).isEqualTo(4);

        JsonNode assetsPerProject = projectAnalytics.get("assetsPerProject");
        JsonNode activeEntry = findByProjectId(assetsPerProject, activeProject);
        assertThat(activeEntry.get("assetCount").asLong()).isEqualTo(2);
    }

    // 27. Collaboration activity is correctly aggregated.
    @Test
    void collaborationAnalytics_shareCountsAndRanking_areAccurate() throws Exception {
        long suffix = System.nanoTime();
        String collaborator1 = "collab1-" + suffix + "@example.com";
        registerAndLogin("collab1-" + suffix, collaborator1, "password123");
        String collaborator2 = "collab2-" + suffix + "@example.com";
        registerAndLogin("collab2-" + suffix, collaborator2, "password123");

        String popularProject = createProject("Popular EP");
        String quietProject = createProject("Quiet EP");
        shareProject(popularProject, collaborator1, "VIEW");
        shareProject(popularProject, collaborator2, "EDIT");
        shareProject(quietProject, collaborator1, "VIEW");

        JsonNode collab = get("/api/v1/analytics/collaboration");
        assertThat(collab.get("totalProjectsShared").asLong()).isEqualTo(2); // 2 distinct projects
        assertThat(collab.get("totalActiveCollaborators").asLong()).isEqualTo(3); // 3 share grants total
        assertThat(collab.get("totalSharesCreated").asLong()).isEqualTo(3);

        JsonNode mostShared = collab.get("mostSharedProjects");
        assertThat(mostShared.get(0).get("projectId").asText()).isEqualTo(popularProject);
        assertThat(mostShared.get(0).get("collaboratorCount").asLong()).isEqualTo(2);
    }

    // 28. Date filtering works: an explicit from/to window that excludes "today" shows zero,
    // one that includes it shows the real count.
    @Test
    void activity_explicitDateRange_filtersCorrectly() throws Exception {
        createProject("EP"); // one PROJECT_CREATED event, timestamped "now"

        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        JsonNode past = get("/api/v1/analytics/activity?from=" + today.minusDays(10) + "&to=" + today.minusDays(2));
        assertThat(past.get("totalEvents").asLong()).isZero();

        JsonNode present = get("/api/v1/analytics/activity?from=" + today + "&to=" + today);
        assertThat(present.get("totalEvents").asLong()).isGreaterThanOrEqualTo(1);
    }

    // 29. 7-day activity works.
    @Test
    void activity_last7Days_includesTodaysEvents() throws Exception {
        createClient("John Smith"); // CLIENT_CREATED, timestamped "now"

        JsonNode activity = get("/api/v1/analytics/activity?days=7");
        assertThat(activity.get("totalEvents").asLong()).isGreaterThanOrEqualTo(1);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        assertThat(LocalDate.parse(activity.get("from").asText())).isEqualTo(today.minusDays(6));
        assertThat(LocalDate.parse(activity.get("to").asText())).isEqualTo(today);
    }

    // 30. 30-day activity works.
    @Test
    void activity_last30Days_includesTodaysEvents() throws Exception {
        createCollection("Favorites"); // COLLECTION_CREATED, timestamped "now"

        JsonNode activity = get("/api/v1/analytics/activity?days=30");
        assertThat(activity.get("totalEvents").asLong()).isGreaterThanOrEqualTo(1);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        assertThat(LocalDate.parse(activity.get("from").asText())).isEqualTo(today.minusDays(29));
    }

    @Test
    void activity_eventTypeFilter_countsOnlyThatType() throws Exception {
        createClient("John Smith"); // CLIENT_CREATED
        createProject("EP");        // PROJECT_CREATED

        JsonNode filtered = get("/api/v1/analytics/activity?days=7&eventType=CLIENT_CREATED");
        assertThat(filtered.get("totalEvents").asLong()).isEqualTo(1);
    }

    // ================= HISTORICAL ANALYTICS =================

    // 31. An Asset is deleted: lifetime play/download totals (event-log based) survive untouched;
    // a still-top-ranked entry for it shows a null title instead of vanishing or erroring.
    @Test
    void historical_deletedAsset_playCountsSurvive_rankingShowsNullTitle() throws Exception {
        String assetId = createAsset("Doomed Beat");
        uploadFile(assetId);
        playAsset(assetId);
        playAsset(assetId);

        long playsBefore = get("/api/v1/analytics/assets").get("totalPlays").asLong();

        mockMvc.perform(delete("/api/v1/assets/" + assetId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = get("/api/v1/analytics/assets");
        assertThat(afterDelete.get("totalPlays").asLong()).isEqualTo(playsBefore); // unchanged
        JsonNode topPlayed = afterDelete.get("topPlayedAssets");
        JsonNode entry = findByAssetId(topPlayed, assetId);
        assertThat(entry).isNotNull(); // the historical ranking entry still exists
        assertThat(entry.get("count").asLong()).isEqualTo(2); // count preserved
        assertThat(entry.get("title").isNull()).isTrue(); // but the asset itself is gone
    }

    // 32. A Project is deleted: PROJECT_UPDATED totals survive; a still-ranked activity entry for
    // it shows a null projectName.
    @Test
    void historical_deletedProject_activityCountsSurvive_rankingShowsNullName() throws Exception {
        String projectId = createProject("Doomed EP");
        updateProject(projectId, "Renamed Once");
        updateProject(projectId, "Renamed Twice");

        long updatesBefore = get("/api/v1/analytics/projects").get("totalProjectUpdates").asLong();

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = get("/api/v1/analytics/projects");
        assertThat(afterDelete.get("totalProjectUpdates").asLong()).isEqualTo(updatesBefore); // unchanged
        JsonNode entry = findByProjectId(afterDelete.get("mostActiveProjects"), projectId);
        assertThat(entry).isNotNull();
        assertThat(entry.get("projectName").isNull()).isTrue();
    }

    // 33. A Client is deleted: this doesn't corrupt any analytics endpoint, and the live
    // totalClients count (a current-state count, not an event count) correctly drops by one.
    @Test
    void historical_deletedClient_doesNotCorruptAnalytics() throws Exception {
        String clientId = createClient("Doomed Client");
        long before = get("/api/v1/analytics/overview").get("totalClients").asLong();

        mockMvc.perform(delete("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode overview = get("/api/v1/analytics/overview");
        assertThat(overview.get("totalClients").asLong()).isEqualTo(before - 1);
        // Every other endpoint must still respond cleanly too.
        assertThat(get("/api/v1/analytics/assets")).isNotNull();
        assertThat(get("/api/v1/analytics/projects")).isNotNull();
        assertThat(get("/api/v1/analytics/collaboration")).isNotNull();
    }

    // --- helpers ---

    private JsonNode get(String path) throws Exception {
        MvcResult result = mockMvc.perform(get0(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get0(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
    }

    private String createAsset(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createAssetInProject(String title, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void uploadFile(String assetId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", VALID_WAV);
        mockMvc.perform(multipart("/api/v1/assets/" + assetId + "/file").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void playAsset(String assetId) throws Exception {
        mockMvc.perform(get0("/api/v1/assets/" + assetId + "/file").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void downloadAsset(String assetId) throws Exception {
        mockMvc.perform(get0("/api/v1/assets/" + assetId + "/file?download=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void updateProject(String projectId, String newName) throws Exception {
        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + newName + "\",\"status\":\"PLANNING\"}"))
                .andExpect(status().isOk());
    }

    private void shareProject(String projectId, String email, String permission) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/shares")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + email + "\",\"permission\":\"" + permission + "\"}"))
                .andExpect(status().isCreated());
    }

    private String createCollection(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/collections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createClient(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTag(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode findByProjectId(JsonNode array, String projectId) {
        for (JsonNode node : array) {
            if (node.get("projectId").asText().equals(projectId)) {
                return node;
            }
        }
        return null;
    }

    private JsonNode findByAssetId(JsonNode array, String assetId) {
        for (JsonNode node : array) {
            if (node.get("assetId").asText().equals(assetId)) {
                return node;
            }
        }
        return null;
    }
}
