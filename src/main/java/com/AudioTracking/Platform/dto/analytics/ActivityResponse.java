package com.AudioTracking.Platform.dto.analytics;

import java.time.LocalDate;
import java.util.List;

// Days with zero matching events simply don't appear in `buckets` -- callers that need every day
// present (e.g. for a chart x-axis) fill the gaps with 0 client-side.
//
// changeFromPreviousPeriodPercent compares totalEvents against an equal-length window immediately
// before `from`. Null (not 0, not infinite) when the previous period had zero events -- there's
// no meaningful percentage change from nothing, and pretending otherwise would misrepresent the
// data. See docs/analytics.md.
public record ActivityResponse(
        LocalDate from,
        LocalDate to,
        long totalEvents,
        Double changeFromPreviousPeriodPercent,
        List<ActivityBucket> buckets
) {
}
