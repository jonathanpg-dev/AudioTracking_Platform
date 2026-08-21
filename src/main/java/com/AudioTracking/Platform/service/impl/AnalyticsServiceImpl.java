package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.entity.AnalyticsEvent;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.repository.AnalyticsEventRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceImpl.class);

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;

    public AnalyticsServiceImpl(AnalyticsEventRepository analyticsEventRepository, UserRepository userRepository) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void record(UUID userId, AnalyticsEventType eventType, UUID assetId, UUID projectId) {
        // Analytics is a secondary concern: a failure recording an event must never break the
        // real action it's attached to (an upload, a share, a delete, ...). Every failure path
        // here is swallowed and logged, deliberately mirroring the existing best-effort R2
        // cleanup pattern in AssetServiceImpl (log.warn, never rethrow).
        try {
            AnalyticsEvent event = new AnalyticsEvent();
            // getReferenceById avoids an extra SELECT: the caller is already an authenticated
            // user resolved from the JWT, so we only need their id to set the FK.
            event.setUser(userRepository.getReferenceById(userId));
            event.setEventType(eventType);
            event.setAssetId(assetId);
            event.setProjectId(projectId);
            analyticsEventRepository.save(event);
        } catch (RuntimeException e) {
            log.warn("Failed to record analytics event {} for user {}: {}", eventType, userId, e.getMessage());
        }
    }
}
