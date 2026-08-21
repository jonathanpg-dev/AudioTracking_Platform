package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.entity.AnalyticsEvent;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.repository.AnalyticsEventRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock private AnalyticsEventRepository analyticsEventRepository;
    @Mock private UserRepository userRepository;

    private AnalyticsServiceImpl analyticsService;

    private final UUID userId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsServiceImpl(analyticsEventRepository, userRepository);
    }

    @Test
    void record_savesEventWithExactlyTheGivenFields() {
        User userRef = new User();
        userRef.setId(userId);
        when(userRepository.getReferenceById(userId)).thenReturn(userRef);

        analyticsService.record(userId, AnalyticsEventType.ASSET_UPLOADED, assetId, projectId);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(analyticsEventRepository).save(captor.capture());
        AnalyticsEvent saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(userRef);
        assertThat(saved.getEventType()).isEqualTo(AnalyticsEventType.ASSET_UPLOADED);
        assertThat(saved.getAssetId()).isEqualTo(assetId);
        assertThat(saved.getProjectId()).isEqualTo(projectId);
    }

    @Test
    void record_nullAssetAndProjectId_isAllowed_forEventsWithNoAssetOrProject() {
        when(userRepository.getReferenceById(userId)).thenReturn(new User());

        analyticsService.record(userId, AnalyticsEventType.CLIENT_CREATED, null, null);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(analyticsEventRepository).save(captor.capture());
        assertThat(captor.getValue().getAssetId()).isNull();
        assertThat(captor.getValue().getProjectId()).isNull();
    }

    // The core "analytics is a secondary concern" guarantee: a failure recording an event must
    // never propagate out and break whatever action it's attached to.
    @Test
    void record_repositoryThrows_isSwallowed_doesNotPropagate() {
        when(userRepository.getReferenceById(userId)).thenReturn(new User());
        doThrow(new RuntimeException("db unavailable")).when(analyticsEventRepository).save(any());

        assertThatCode(() -> analyticsService.record(userId, AnalyticsEventType.ASSET_PLAYED, assetId, null))
                .doesNotThrowAnyException();
    }

    @Test
    void record_userLookupThrows_isAlsoSwallowed() {
        when(userRepository.getReferenceById(userId)).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> analyticsService.record(userId, AnalyticsEventType.ASSET_PLAYED, assetId, null))
                .doesNotThrowAnyException();

        verify(analyticsEventRepository, never()).save(any());
    }
}
