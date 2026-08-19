package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers Phase 3 STEP 7: filtering and pagination on GET /assets specifically.
class AssetFilterIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        token = registerAndLogin("assetFilter" + suffix, "assetFilter" + suffix + "@example.com", "password123");
    }

    private String createAsset(String title, String assetType, Integer bpm, String musicalKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","assetType":"%s","bpm":%s,"musicalKey":"%s"}
                                """.formatted(title, assetType, bpm, musicalKey)))
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

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- No filters: must behave exactly like before this feature existed ---

    @Test
    void noFiltersNoPagination_returnsEverything() throws Exception {
        createAsset("Beat One", "BEAT", 140, "Cm");
        createAsset("Sample One", "SAMPLE", 90, "Gm");

        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // --- assetType filter ---

    @Test
    void filterByAssetType_returnsOnlyMatchingType() throws Exception {
        createAsset("Beat One", "BEAT", null, null);
        createAsset("Sample One", "SAMPLE", null, null);

        mockMvc.perform(get("/api/v1/assets?assetType=BEAT").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Beat One"));
    }

    // --- project filter — the LEFT JOIN correctness case ---

    @Test
    void filterByProjectId_returnsOnlyAssetsInThatProject() throws Exception {
        String projectId = createProject("R&B EP");
        String assetId = createAsset("In Project", "BEAT", null, null);
        mockMvc.perform(put("/api/v1/assets/" + assetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"In Project\",\"assetType\":\"BEAT\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isOk());
        createAsset("Standalone", "BEAT", null, null); // no project

        mockMvc.perform(get("/api/v1/assets?projectId=" + projectId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("In Project"));
    }

    @Test
    void noProjectFilter_stillIncludesAssetsWithNoProject() throws Exception {
        // This is exactly the case that breaks with an implicit inner join instead of LEFT JOIN:
        // an unfiltered request must not silently drop assets that have no project at all.
        createAsset("Standalone", "BEAT", null, null);

        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // --- tag filter — the LEFT JOIN + DISTINCT correctness case ---

    @Test
    void filterByTagId_returnsOnlyAssetsWithThatTag() throws Exception {
        String tagId = createTag("trap");
        String taggedAssetId = createAsset("Tagged", "BEAT", null, null);
        mockMvc.perform(post("/api/v1/assets/" + taggedAssetId + "/tags/" + tagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        createAsset("Untagged", "BEAT", null, null);

        mockMvc.perform(get("/api/v1/assets?tagId=" + tagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Tagged"));
    }

    @Test
    void noTagFilter_stillIncludesAssetsWithNoTags() throws Exception {
        createAsset("Untagged", "BEAT", null, null);

        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void assetWithMultipleTags_appearsExactlyOnce_notOncePerTag() throws Exception {
        // This is the DISTINCT case: the tags join is multi-valued, so without SELECT DISTINCT
        // this asset would appear 3 times (once per tag row) instead of once.
        String assetId = createAsset("Multi-tagged", "BEAT", null, null);
        for (String tagName : new String[]{"trap", "dark", "cinematic"}) {
            String tagId = createTag(tagName);
            mockMvc.perform(post("/api/v1/assets/" + assetId + "/tags/" + tagId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // --- BPM range filter ---

    @Test
    void filterByBpmRange_returnsOnlyAssetsWithinRange() throws Exception {
        createAsset("Slow", "BEAT", 80, null);
        createAsset("Medium", "BEAT", 140, null);
        createAsset("Fast", "BEAT", 200, null);

        mockMvc.perform(get("/api/v1/assets?minBpm=100&maxBpm=180").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Medium"));
    }

    // --- musicalKey filter ---

    @Test
    void filterByMusicalKey_exactMatch() throws Exception {
        createAsset("In Cm", "BEAT", null, "Cm");
        createAsset("In Gm", "BEAT", null, "Gm");

        mockMvc.perform(get("/api/v1/assets?musicalKey=Cm").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("In Cm"));
    }

    // --- Combining filters ---

    @Test
    void combiningFilters_appliesAllOfThemTogether() throws Exception {
        createAsset("Match", "BEAT", 140, "Cm");
        createAsset("Wrong type", "SAMPLE", 140, "Cm");
        createAsset("Wrong bpm", "BEAT", 90, "Cm");

        mockMvc.perform(get("/api/v1/assets?assetType=BEAT&minBpm=130&maxBpm=150&musicalKey=Cm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Match"));
    }

    @Test
    void filterMatchingNothing_returnsEmptyList_notAnError() throws Exception {
        createAsset("Beat One", "BEAT", 140, null);

        mockMvc.perform(get("/api/v1/assets?assetType=SAMPLE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- Pagination ---

    @Test
    void pagination_splitsResultsAcrossPages() throws Exception {
        createAsset("First", "BEAT", null, null);
        createAsset("Second", "BEAT", null, null);
        createAsset("Third", "BEAT", null, null);

        mockMvc.perform(get("/api/v1/assets?page=0&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/assets?page=1&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void pagination_combinedWithFilter_paginatesOnlyTheFilteredSet() throws Exception {
        createAsset("Beat A", "BEAT", null, null);
        createAsset("Beat B", "BEAT", null, null);
        createAsset("Sample A", "SAMPLE", null, null);

        mockMvc.perform(get("/api/v1/assets?assetType=BEAT&page=0&size=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
