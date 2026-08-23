// Mirrors AnalyticsEventType.java -- used only as the optional ?eventType= filter on the
// activity endpoint.
export const ANALYTICS_EVENT_TYPES = [
  'ASSET_UPLOADED',
  'ASSET_PLAYED',
  'ASSET_DOWNLOADED',
  'ASSET_DELETED',
  'PROJECT_CREATED',
  'PROJECT_UPDATED',
  'PROJECT_SHARED',
  'COLLECTION_CREATED',
  'CLIENT_CREATED',
] as const
export type AnalyticsEventType = (typeof ANALYTICS_EVENT_TYPES)[number]

// Mirrors AnalyticsOverviewResponse.java.
export interface AnalyticsOverview {
  totalAssets: number
  totalProjects: number
  totalCollections: number
  totalClients: number
  totalTags: number
  totalStorageBytes: number
  totalPlays: number
  totalDownloads: number
  totalActiveCollaborators: number
}

// Mirrors AssetRankingEntry.java. title is null when the Asset has since been deleted.
export interface AssetRankingEntry {
  assetId: string
  title: string | null
  count: number
}

// Mirrors AssetAnalyticsResponse.java.
export interface AssetAnalytics {
  totalUploads: number
  totalPlays: number
  totalDownloads: number
  totalDeletions: number
  topPlayedAssets: AssetRankingEntry[]
  topDownloadedAssets: AssetRankingEntry[]
}

// Mirrors ProjectActivityEntry.java. projectName is null when the Project has since been deleted.
export interface ProjectActivityEntry {
  projectId: string
  projectName: string | null
  eventCount: number
}

// Mirrors ProjectAssetCountEntry.java.
export interface ProjectAssetCountEntry {
  projectId: string
  projectName: string | null
  assetCount: number
}

// Mirrors ProjectAnalyticsResponse.java.
export interface ProjectAnalytics {
  totalProjects: number
  totalProjectUpdates: number
  mostActiveProjects: ProjectActivityEntry[]
  assetsPerProject: ProjectAssetCountEntry[]
}

// Mirrors SharedProjectEntry.java.
export interface SharedProjectEntry {
  projectId: string
  projectName: string | null
  collaboratorCount: number
}

// Mirrors CollaborationAnalyticsResponse.java.
export interface CollaborationAnalytics {
  totalProjectsShared: number
  totalActiveCollaborators: number
  totalSharesCreated: number
  mostSharedProjects: SharedProjectEntry[]
}

// Mirrors ActivityBucket.java.
export interface ActivityBucket {
  date: string
  count: number
}

// Mirrors ActivityResponse.java.
export interface Activity {
  from: string
  to: string
  totalEvents: number
  changeFromPreviousPeriodPercent: number | null
  buckets: ActivityBucket[]
}

// Mirrors GET /analytics/activity's query params.
export interface ActivityQuery {
  days?: number
  from?: string
  to?: string
  eventType?: AnalyticsEventType
}
