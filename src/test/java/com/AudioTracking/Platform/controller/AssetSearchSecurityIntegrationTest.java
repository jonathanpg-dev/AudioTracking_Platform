package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Security-focused regression coverage for the GET /assets search/filter/sort feature
// (AssetFilter.java, AssetRepository#search, SortParams.java): every new query parameter is
// user-controlled input reaching a JPQL query and a Sort clause, so this specifically targets
// injection, cross-user data isolation, and malformed-input handling -- not just "does the
// filter work" (AssetFilterIntegrationTest already covers that).
class AssetSearchSecurityIntegrationTest extends BaseIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        long suffix = System.nanoTime();
        tokenA = registerAndLogin("searchSecA" + suffix, "searchSecA" + suffix + "@example.com", "password123");
        tokenB = registerAndLogin("searchSecB" + suffix, "searchSecB" + suffix + "@example.com", "password123");
    }

    private String createAsset(String token, String title, Integer durationSeconds, String musicalKey, String audioFormat) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","assetType":"BEAT","durationSeconds":%s,"musicalKey":%s,"audioFormat":%s}
                                """.formatted(
                                title,
                                durationSeconds,
                                musicalKey == null ? null : "\"" + musicalKey + "\"",
                                audioFormat == null ? null : "\"" + audioFormat + "\"")))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTag(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // --- Authentication is still enforced on every new query param combination ---

    @Test
    void getAssets_withFiltersButNoAuth_returns401_neverReachesTheQuery() throws Exception {
        mockMvc.perform(get("/api/v1/assets?tagIds=" + java.util.UUID.randomUUID()
                        + "&musicalKey=Cm&audioFormat=wav&minDurationSeconds=0&maxDurationSeconds=30&sortBy=updatedAt&sortDir=asc"))
                .andExpect(status().isUnauthorized());
    }

    // --- Cross-user isolation: no filter combination can surface another user's assets ---

    @Test
    void filterByAnotherUsersTagId_neverReturnsTheirAsset_evenThoughTheTagMatches() throws Exception {
        String tagId = createTag(tokenA, "trap");
        String userAAssetId = createAsset(tokenA, "User A's beat", null, null, null);
        mockMvc.perform(post("/api/v1/assets/" + userAAssetId + "/tags/" + tagId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        createAsset(tokenB, "User B's own beat", null, null, null); // no tags

        // User B filters by User A's real tag id. a.user.id = :userId is unconditional in the
        // query -- this must come back empty (User B owns nothing with that tag), never User A's
        // asset, and never an error revealing whether the tag id even exists.
        mockMvc.perform(get("/api/v1/assets?tagIds=" + tagId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void filtersCombinedWithAnotherUsersData_stillNeverCrossTheOwnershipBoundary() throws Exception {
        // User A has an asset matching every filter User B is about to search with.
        createAsset(tokenA, "Matches everything", 95, "Cm", "wav");
        // User B has nothing.
        mockMvc.perform(get("/api/v1/assets?musicalKey=Cm&audioFormat=wav&minDurationSeconds=60&maxDurationSeconds=120")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- SQL-injection-style payloads are inert: treated as literal values, never as SQL ---

    @Test
    void musicalKeyContainingSqlMetacharacters_isTreatedAsALiteralString_notExecuted() throws Exception {
        createAsset(tokenA, "Real asset", null, "Cm", null);

        mockMvc.perform(get("/api/v1/assets?musicalKey=" + java.net.URLEncoder.encode("' OR '1'='1", "UTF-8"))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty()); // if this were ever interpreted as SQL, it would match everything instead

        mockMvc.perform(get("/api/v1/assets?musicalKey=" + java.net.URLEncoder.encode("x'; DROP TABLE asset; --", "UTF-8"))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // The table must still exist and be queryable afterward -- proves the payload never ran.
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void audioFormatContainingSqlMetacharacters_isTreatedAsALiteralString() throws Exception {
        createAsset(tokenA, "Real asset", null, null, "wav");

        mockMvc.perform(get("/api/v1/assets?audioFormat=" + java.net.URLEncoder.encode("wav' OR '1'='1", "UTF-8"))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- sortBy is whitelisted: an unrecognized or malicious field name never reaches Hibernate ---

    @Test
    void sortByUnrecognizedField_fallsBackToCreatedAt_doesNotError() throws Exception {
        createAsset(tokenA, "First", null, null, null);
        createAsset(tokenA, "Second", null, null, null);

        // Neither a nonexistent property name nor a raw injection attempt should ever produce a
        // 500 (which could itself leak entity/property structure) -- SortParams.ALLOWED_FIELDS
        // silently substitutes the default (createdAt desc) for anything not on the whitelist.
        mockMvc.perform(get("/api/v1/assets?sortBy=user.passwordHash").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Second"))
                .andExpect(jsonPath("$[1].title").value("First"));

        mockMvc.perform(get("/api/v1/assets?sortBy=" + java.net.URLEncoder.encode("createdAt; DROP TABLE asset; --", "UTF-8"))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // --- Malformed input produces a clean 400, never a 500 with a leaked stack trace ---

    @Test
    void malformedTagIdInFilter_returns400_notServerError() throws Exception {
        mockMvc.perform(get("/api/v1/assets?tagIds=not-a-valid-uuid").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedDurationBounds_returns400_notServerError() throws Exception {
        mockMvc.perform(get("/api/v1/assets?minDurationSeconds=notANumber").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void negativeDurationBounds_doesNotError_justFiltersNormally() throws Exception {
        // Not a validation error -- a filter value out of the "sensible" range should just narrow
        // (or not narrow) results, not crash. CreateAssetRequest.durationSeconds itself already
        // rejects negative values at write time (@PositiveOrZero); this is read-side filtering.
        createAsset(tokenA, "Short clip", 5, null, null);

        mockMvc.perform(get("/api/v1/assets?minDurationSeconds=-100").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
