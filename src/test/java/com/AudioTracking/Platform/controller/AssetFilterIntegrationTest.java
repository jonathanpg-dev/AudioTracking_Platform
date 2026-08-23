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
        return createAsset(title, assetType, bpm, musicalKey, null, null);
    }

    private String createAsset(String title, String assetType, Integer bpm, String musicalKey,
                                Integer durationSeconds, String audioFormat) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","assetType":"%s","bpm":%s,"musicalKey":"%s","durationSeconds":%s,"audioFormat":%s}
                                """.formatted(title, assetType, bpm, musicalKey, durationSeconds,
                                audioFormat == null ? null : "\"" + audioFormat + "\"")))
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

    // --- tag filter — AND semantics: an asset must carry every listed tag, not just one ---

    @Test
    void filterBySingleTagId_returnsOnlyAssetsWithThatTag() throws Exception {
        String tagId = createTag("trap");
        String taggedAssetId = createAsset("Tagged", "BEAT", null, null);
        mockMvc.perform(post("/api/v1/assets/" + taggedAssetId + "/tags/" + tagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        createAsset("Untagged", "BEAT", null, null);

        mockMvc.perform(get("/api/v1/assets?tagIds=" + tagId).header("Authorization", "Bearer " + token))
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
        // Guards against a fan-out regression: tag matching is a correlated subquery (see
        // AssetRepository#search), not a join, specifically so a multi-tagged asset can't appear
        // more than once in the result.
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

    @Test
    void filterByMultipleTagIds_matchesOnlyAssetsWithEveryOneOfThem() throws Exception {
        String trapTagId = createTag("trap");
        String darkTagId = createTag("dark");

        String bothId = createAsset("Both", "BEAT", null, null);
        mockMvc.perform(post("/api/v1/assets/" + bothId + "/tags/" + trapTagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assets/" + bothId + "/tags/" + darkTagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String trapOnlyId = createAsset("Trap only", "BEAT", null, null);
        mockMvc.perform(post("/api/v1/assets/" + trapOnlyId + "/tags/" + trapTagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // AND semantics: requesting both tags must exclude the asset that only has one of them.
        mockMvc.perform(get("/api/v1/assets?tagIds=" + trapTagId + "&tagIds=" + darkTagId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Both"));
    }

    @Test
    void filterByDuplicateTagId_stillMatches_dedupedBeforeCounting() throws Exception {
        // Regression test: tagCount must be the number of *distinct* requested tags, not the raw
        // number of tagIds params sent -- otherwise a duplicated id makes the AND-match
        // unsatisfiable and silently returns nothing. See AssetServiceImpl#getAssets.
        String tagId = createTag("trap");
        String taggedAssetId = createAsset("Tagged", "BEAT", null, null);
        mockMvc.perform(post("/api/v1/assets/" + taggedAssetId + "/tags/" + tagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets?tagIds=" + tagId + "&tagIds=" + tagId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Tagged"));
    }

    // --- format filter ---

    @Test
    void filterByAudioFormat_returnsOnlyMatchingFormat() throws Exception {
        createAsset("A Wav", "BEAT", null, null, null, "wav");
        createAsset("A Mp3", "BEAT", null, null, null, "mp3");

        mockMvc.perform(get("/api/v1/assets?audioFormat=wav").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("A Wav"));
    }

    // --- duration filter — the 30-second-bracket range the frontend sends as min/max seconds ---

    @Test
    void filterByDurationRange_returnsOnlyAssetsWithinIt() throws Exception {
        createAsset("Short", "BEAT", null, null, 15, null); // 0:00-0:30 bracket
        createAsset("Medium", "BEAT", null, null, 75, null); // 1:00-1:30 bracket
        createAsset("Long", "BEAT", null, null, 200, null); // well past both brackets

        // Requesting the 0:30-1:30 window (two 30s brackets) should include "Medium" (75s) but
        // exclude "Short" (15s, below it) and "Long" (200s, above it).
        mockMvc.perform(get("/api/v1/assets?minDurationSeconds=30&maxDurationSeconds=89")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Medium"));
    }

    // --- musicalKey filter is case-insensitive ---

    @Test
    void filterByMusicalKey_isCaseInsensitive() throws Exception {
        createAsset("In Cm", "BEAT", null, "Cm");

        mockMvc.perform(get("/api/v1/assets?musicalKey=cm").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("In Cm"));
    }

    // --- sort ---

    @Test
    void sortByCreatedAtAscending_returnsOldestFirst() throws Exception {
        createAsset("First", "BEAT", null, null);
        createAsset("Second", "BEAT", null, null);

        mockMvc.perform(get("/api/v1/assets?sortBy=createdAt&sortDir=asc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].title").value("Second"));
    }

    @Test
    void sortByUpdatedAtDescending_mostRecentlyModifiedFirst() throws Exception {
        String firstId = createAsset("First", "BEAT", null, null);
        createAsset("Second", "BEAT", null, null);

        // Touch "First" after both were created, so it becomes the more recently *modified* one
        // even though it was created first -- this is what distinguishes updatedAt from createdAt.
        mockMvc.perform(put("/api/v1/assets/" + firstId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First\",\"assetType\":\"BEAT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets?sortBy=updatedAt&sortDir=desc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].title").value("Second"));
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
