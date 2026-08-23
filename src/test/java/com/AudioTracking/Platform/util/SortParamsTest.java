package com.AudioTracking.Platform.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

// SortParams.resolve is the whitelist standing between user-controlled `sortBy`/`sortDir` query
// params and a Spring Data Sort object -- see AssetSearchSecurityIntegrationTest and the
// ProjectControllerIntegrationTest/CollectionControllerIntegrationTest sort-whitelist tests for
// the same contract verified end-to-end over HTTP. This is the fast, exhaustive unit-level check.
class SortParamsTest {

    @Test
    void bothNull_defaultsToCreatedAtDescending() {
        assertThat(SortParams.resolve(null, null)).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void recognizedFieldsAreHonored() {
        assertThat(SortParams.resolve("createdAt", "asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(SortParams.resolve("updatedAt", "desc")).isEqualTo(Sort.by(Sort.Direction.DESC, "updatedAt"));
        assertThat(SortParams.resolve("updatedAt", "asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "updatedAt"));
    }

    @Test
    void directionIsCaseInsensitive_andAnythingOtherThanAscMeansDescending() {
        assertThat(SortParams.resolve("createdAt", "ASC")).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(SortParams.resolve("createdAt", "AsC")).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(SortParams.resolve("createdAt", "sideways")).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(SortParams.resolve("createdAt", "")).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void unrecognizedOrMaliciousFieldNamesFallBackToCreatedAt_neverPassedThrough() {
        // Not on the whitelist -- must never reach Hibernate as a literal property/sort path.
        assertThat(SortParams.resolve("passwordHash", "asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(SortParams.resolve("user.passwordHash", "asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(SortParams.resolve("createdAt; DROP TABLE asset; --", "desc"))
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(SortParams.resolve("", "desc")).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void isCaseSensitiveOnTheFieldName_camelCaseMismatchFallsBack() {
        // Deliberate: silently "fixing" a near-miss like "CreatedAt" would make the whitelist's
        // actual boundary less obvious than just defaulting.
        assertThat(SortParams.resolve("CreatedAt", "asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
    }
}
