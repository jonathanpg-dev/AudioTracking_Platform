package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.analytics.ActivityResponse;
import com.AudioTracking.Platform.dto.analytics.AnalyticsOverviewResponse;
import com.AudioTracking.Platform.dto.analytics.AssetAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.AssetRankingEntry;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.repository.AnalyticsEventRepository;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.CollectionRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceImplTest {

    @Mock private AnalyticsEventRepository analyticsEventRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private TagRepository tagRepository;
    @Mock private ProjectShareRepository projectShareRepository;

    private AnalyticsQueryServiceImpl queryService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new AnalyticsQueryServiceImpl(analyticsEventRepository, assetRepository, projectRepository,
                collectionRepository, clientRepository, tagRepository, projectShareRepository);
    }

    @Test
    void getOverview_assemblesEveryCountFromItsOwnSource() {
        when(assetRepository.countByUserId(userId)).thenReturn(3L);
        when(projectRepository.countByUserId(userId)).thenReturn(2L);
        when(collectionRepository.countByUserId(userId)).thenReturn(1L);
        when(clientRepository.countByUserId(userId)).thenReturn(4L);
        when(tagRepository.countByUserId(userId)).thenReturn(5L);
        when(assetRepository.sumFileSizeBytesByUserId(userId)).thenReturn(123456L);
        when(analyticsEventRepository.countByUserIdAndEventType(userId, AnalyticsEventType.ASSET_PLAYED)).thenReturn(10L);
        when(analyticsEventRepository.countByUserIdAndEventType(userId, AnalyticsEventType.ASSET_DOWNLOADED)).thenReturn(7L);
        when(projectShareRepository.countByProject_UserId(userId)).thenReturn(2L);

        AnalyticsOverviewResponse response = queryService.getOverview(userId);

        assertThat(response).isEqualTo(new AnalyticsOverviewResponse(3L, 2L, 1L, 4L, 5L, 123456L, 10L, 7L, 2L));
    }

    @Test
    void getAssetAnalytics_topPlayedAsset_stillExisting_includesTitle() {
        UUID assetId = UUID.randomUUID();
        AnalyticsEventRepository.AssetEventCount row = mock(AnalyticsEventRepository.AssetEventCount.class);
        when(row.getAssetId()).thenReturn(assetId);
        when(row.getEventCount()).thenReturn(9L);
        when(analyticsEventRepository.findTopAssetsByEventType(eq(userId), eq("ASSET_PLAYED"), any()))
                .thenReturn(List.of(row));
        when(analyticsEventRepository.findTopAssetsByEventType(eq(userId), eq("ASSET_DOWNLOADED"), any()))
                .thenReturn(List.of());

        Asset asset = new Asset();
        asset.setId(assetId);
        asset.setTitle("Dark Trap Loop");
        when(assetRepository.findAllById(List.of(assetId))).thenReturn(List.of(asset));

        AssetAnalyticsResponse response = queryService.getAssetAnalytics(userId, 5);

        assertThat(response.topPlayedAssets()).containsExactly(new AssetRankingEntry(assetId, "Dark Trap Loop", 9L));
    }

    // The historical-analytics guarantee: a ranking entry for an Asset that's since been deleted
    // still appears (the count/history isn't lost), just with a null title instead of a crash or
    // a silently dropped row.
    @Test
    void getAssetAnalytics_topPlayedAsset_sinceDeleted_hasNullTitle_notMissingOrError() {
        UUID deletedAssetId = UUID.randomUUID();
        AnalyticsEventRepository.AssetEventCount row = mock(AnalyticsEventRepository.AssetEventCount.class);
        when(row.getAssetId()).thenReturn(deletedAssetId);
        when(row.getEventCount()).thenReturn(4L);
        when(analyticsEventRepository.findTopAssetsByEventType(eq(userId), eq("ASSET_PLAYED"), any()))
                .thenReturn(List.of(row));
        when(analyticsEventRepository.findTopAssetsByEventType(eq(userId), eq("ASSET_DOWNLOADED"), any()))
                .thenReturn(List.of());
        // The asset no longer exists -- findAllById simply omits it, same as a real deleted row would.
        when(assetRepository.findAllById(List.of(deletedAssetId))).thenReturn(List.of());

        AssetAnalyticsResponse response = queryService.getAssetAnalytics(userId, 5);

        assertThat(response.topPlayedAssets()).containsExactly(new AssetRankingEntry(deletedAssetId, null, 4L));
    }

    @Test
    void getAssetAnalytics_noEventsAtAll_returnsEmptyRankings_notNull() {
        when(analyticsEventRepository.findTopAssetsByEventType(any(), any(), any())).thenReturn(List.of());

        AssetAnalyticsResponse response = queryService.getAssetAnalytics(userId, 5);

        assertThat(response.topPlayedAssets()).isEmpty();
        assertThat(response.topDownloadedAssets()).isEmpty();
    }

    @Test
    void getActivity_daysParam_resolvesFromAndToRelativeToToday() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(analyticsEventRepository.countByUserIdAndTimestampBetween(eq(userId), any(), any())).thenReturn(0L);
        when(analyticsEventRepository.findDailyActivity(eq(userId), any(), any(), isNull())).thenReturn(List.of());

        ActivityResponse response = queryService.getActivity(userId, 7, null, null, null);

        assertThat(response.to()).isEqualTo(today);
        assertThat(response.from()).isEqualTo(today.minusDays(6)); // 7 days inclusive of today
    }

    @Test
    void getActivity_explicitFromAfterTo_throwsIllegalArgument() {
        LocalDate from = LocalDate.of(2026, 1, 10);
        LocalDate to = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> queryService.getActivity(userId, null, from, to, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getActivity_previousPeriodHadZeroEvents_changePercentIsNull_notInfinite() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);
        when(analyticsEventRepository.countByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(5L)  // current period total
                .thenReturn(0L); // previous period total
        when(analyticsEventRepository.findDailyActivity(eq(userId), any(), any(), isNull())).thenReturn(List.of());

        ActivityResponse response = queryService.getActivity(userId, null, from, to, null);

        assertThat(response.totalEvents()).isEqualTo(5L);
        assertThat(response.changeFromPreviousPeriodPercent()).isNull();
    }

    @Test
    void getActivity_previousPeriodHadEvents_computesPercentChangeCorrectly() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);
        when(analyticsEventRepository.countByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(15L)  // current period: 15 events
                .thenReturn(10L); // previous period: 10 events -> +50%
        when(analyticsEventRepository.findDailyActivity(eq(userId), any(), any(), isNull())).thenReturn(List.of());

        ActivityResponse response = queryService.getActivity(userId, null, from, to, null);

        assertThat(response.changeFromPreviousPeriodPercent()).isEqualTo(50.0);
    }

    @Test
    void getActivity_eventTypeFilter_isPassedThroughAsItsName() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);
        when(analyticsEventRepository.countByUserIdAndEventTypeAndTimestampBetween(eq(userId), eq(AnalyticsEventType.PROJECT_SHARED), any(), any()))
                .thenReturn(3L);
        when(analyticsEventRepository.findDailyActivity(eq(userId), any(), any(), eq("PROJECT_SHARED"))).thenReturn(List.of());

        ActivityResponse response = queryService.getActivity(userId, null, from, to, AnalyticsEventType.PROJECT_SHARED);

        assertThat(response.totalEvents()).isEqualTo(3L);
        org.mockito.Mockito.verify(analyticsEventRepository, org.mockito.Mockito.never())
                .countByUserIdAndTimestampBetween(any(), any(), any());
    }
}
