package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.analytics.ActivityBucket;
import com.AudioTracking.Platform.dto.analytics.ActivityResponse;
import com.AudioTracking.Platform.dto.analytics.AnalyticsOverviewResponse;
import com.AudioTracking.Platform.dto.analytics.AssetAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.AssetRankingEntry;
import com.AudioTracking.Platform.dto.analytics.CollaborationAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.ProjectActivityEntry;
import com.AudioTracking.Platform.dto.analytics.ProjectAnalyticsResponse;
import com.AudioTracking.Platform.dto.analytics.ProjectAssetCountEntry;
import com.AudioTracking.Platform.dto.analytics.SharedProjectEntry;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.repository.AnalyticsEventRepository;
import com.AudioTracking.Platform.repository.AssetRepository;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.CollectionRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.ProjectShareRepository;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.service.AnalyticsQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_DELETED;
import static com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_DOWNLOADED;
import static com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_PLAYED;
import static com.AudioTracking.Platform.entity.AnalyticsEventType.ASSET_UPLOADED;
import static com.AudioTracking.Platform.entity.AnalyticsEventType.PROJECT_SHARED;
import static com.AudioTracking.Platform.entity.AnalyticsEventType.PROJECT_UPDATED;

@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    // Days back from "to" when neither `days` nor `from` is given.
    private static final int DEFAULT_ACTIVITY_DAYS = 30;

    private final AnalyticsEventRepository analyticsEventRepository;
    private final AssetRepository assetRepository;
    private final ProjectRepository projectRepository;
    private final CollectionRepository collectionRepository;
    private final ClientRepository clientRepository;
    private final TagRepository tagRepository;
    private final ProjectShareRepository projectShareRepository;

    public AnalyticsQueryServiceImpl(AnalyticsEventRepository analyticsEventRepository, AssetRepository assetRepository,
                                      ProjectRepository projectRepository, CollectionRepository collectionRepository,
                                      ClientRepository clientRepository, TagRepository tagRepository,
                                      ProjectShareRepository projectShareRepository) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.assetRepository = assetRepository;
        this.projectRepository = projectRepository;
        this.collectionRepository = collectionRepository;
        this.clientRepository = clientRepository;
        this.tagRepository = tagRepository;
        this.projectShareRepository = projectShareRepository;
    }

    @Override
    public AnalyticsOverviewResponse getOverview(UUID userId) {
        return new AnalyticsOverviewResponse(
                assetRepository.countByUserId(userId),
                projectRepository.countByUserId(userId),
                collectionRepository.countByUserId(userId),
                clientRepository.countByUserId(userId),
                tagRepository.countByUserId(userId),
                assetRepository.sumFileSizeBytesByUserId(userId),
                analyticsEventRepository.countByUserIdAndEventType(userId, ASSET_PLAYED),
                analyticsEventRepository.countByUserIdAndEventType(userId, ASSET_DOWNLOADED),
                projectShareRepository.countByProject_UserId(userId));
    }

    @Override
    public AssetAnalyticsResponse getAssetAnalytics(UUID userId, int topN) {
        return new AssetAnalyticsResponse(
                analyticsEventRepository.countByUserIdAndEventType(userId, ASSET_UPLOADED),
                analyticsEventRepository.countByUserIdAndEventType(userId, ASSET_PLAYED),
                analyticsEventRepository.countByUserIdAndEventType(userId, ASSET_DOWNLOADED),
                analyticsEventRepository.countByUserIdAndEventType(userId, ASSET_DELETED),
                rankAssetsByEventType(userId, ASSET_PLAYED, topN),
                rankAssetsByEventType(userId, ASSET_DOWNLOADED, topN));
    }

    @Override
    public ProjectAnalyticsResponse getProjectAnalytics(UUID userId, int topN) {
        return new ProjectAnalyticsResponse(
                projectRepository.countByUserId(userId),
                analyticsEventRepository.countByUserIdAndEventType(userId, PROJECT_UPDATED),
                rankProjectsByActivity(userId, topN),
                assetsPerProject(userId));
    }

    @Override
    public CollaborationAnalyticsResponse getCollaborationAnalytics(UUID userId, int topN) {
        List<SharedProjectEntry> mostShared = projectShareRepository
                .findMostSharedProjects(userId, PageRequest.of(0, topN)).stream()
                .map(row -> new SharedProjectEntry(row.getProjectId(), row.getProjectName(), row.getShareCount()))
                .toList();

        return new CollaborationAnalyticsResponse(
                projectShareRepository.countDistinctProjectsByProjectOwnerId(userId),
                projectShareRepository.countByProject_UserId(userId),
                analyticsEventRepository.countByUserIdAndEventType(userId, PROJECT_SHARED),
                mostShared);
    }

    @Override
    public ActivityResponse getActivity(UUID userId, Integer days, LocalDate from, LocalDate to, AnalyticsEventType eventType) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays((days != null ? days : DEFAULT_ACTIVITY_DAYS) - 1L);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }

        Instant fromInstant = resolvedFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = resolvedTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(); // exclusive, so `to` day is fully included

        long totalEvents = countInRange(userId, eventType, fromInstant, toInstant);

        // Compare against an equal-length window immediately before `from` -- null (not 0, not
        // infinite) when that previous window had no events at all, since there's no meaningful
        // percentage change from nothing. See ActivityResponse.
        long periodLengthDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
        Instant previousFromInstant = fromInstant.minus(periodLengthDays, ChronoUnit.DAYS);
        long previousTotal = countInRange(userId, eventType, previousFromInstant, fromInstant);
        Double changePercent = previousTotal == 0
                ? null
                : ((double) (totalEvents - previousTotal) / previousTotal) * 100.0;

        List<ActivityBucket> buckets = analyticsEventRepository
                .findDailyActivity(userId, fromInstant, toInstant, eventType == null ? null : eventType.name())
                .stream()
                .map(row -> new ActivityBucket(row.getDay(), row.getEventCount()))
                .toList();

        return new ActivityResponse(resolvedFrom, resolvedTo, totalEvents, changePercent, buckets);
    }

    private long countInRange(UUID userId, AnalyticsEventType eventType, Instant from, Instant to) {
        return eventType == null
                ? analyticsEventRepository.countByUserIdAndTimestampBetween(userId, from, to)
                : analyticsEventRepository.countByUserIdAndEventTypeAndTimestampBetween(userId, eventType, from, to);
    }

    private List<AssetRankingEntry> rankAssetsByEventType(UUID userId, AnalyticsEventType eventType, int topN) {
        List<AnalyticsEventRepository.AssetEventCount> rows =
                analyticsEventRepository.findTopAssetsByEventType(userId, eventType.name(), PageRequest.of(0, topN));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> titlesById = titlesById(rows.stream().map(AnalyticsEventRepository.AssetEventCount::getAssetId).toList());
        return rows.stream()
                .map(row -> new AssetRankingEntry(row.getAssetId(), titlesById.get(row.getAssetId()), row.getEventCount()))
                .toList();
    }

    private List<ProjectActivityEntry> rankProjectsByActivity(UUID userId, int topN) {
        List<AnalyticsEventRepository.ProjectEventCount> rows =
                analyticsEventRepository.findTopProjectsByActivity(userId, PageRequest.of(0, topN));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> namesById = projectNamesById(rows.stream().map(AnalyticsEventRepository.ProjectEventCount::getProjectId).toList());
        return rows.stream()
                .map(row -> new ProjectActivityEntry(row.getProjectId(), namesById.get(row.getProjectId()), row.getEventCount()))
                .toList();
    }

    private List<ProjectAssetCountEntry> assetsPerProject(UUID userId) {
        List<AssetRepository.ProjectAssetCount> rows = assetRepository.countAssetsGroupedByProject(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> namesById = projectNamesById(rows.stream().map(AssetRepository.ProjectAssetCount::getProjectId).toList());
        return rows.stream()
                .map(row -> new ProjectAssetCountEntry(row.getProjectId(), namesById.get(row.getProjectId()), row.getAssetCount()))
                .toList();
    }

    // A deleted Asset/Project's id has no matching row here, and Map.get() on a missing key
    // returns null -- exactly the "title/name unavailable" signal AssetRankingEntry/
    // ProjectActivityEntry document. One batch lookup per ranking, never one query per row.
    private Map<UUID, String> titlesById(List<UUID> assetIds) {
        return assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Asset::getTitle));
    }

    private Map<UUID, String> projectNamesById(List<UUID> projectIds) {
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
    }
}
