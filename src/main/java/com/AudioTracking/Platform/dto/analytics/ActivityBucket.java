package com.AudioTracking.Platform.dto.analytics;

import java.time.LocalDate;

public record ActivityBucket(
        LocalDate date,
        long count
) {
}
