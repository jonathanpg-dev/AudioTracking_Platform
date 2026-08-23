import { api } from './client'
import { toQueryString } from '@/utils/queryString'
import type { Activity, ActivityQuery, AnalyticsOverview, AssetAnalytics, CollaborationAnalytics, ProjectAnalytics } from '@/types/analytics'

export const analyticsApi = {
  overview: () => api.get<AnalyticsOverview>('/api/v1/analytics/overview'),
  assets: (topN?: number) => api.get<AssetAnalytics>(`/api/v1/analytics/assets${toQueryString({ topN })}`),
  projects: (topN?: number) => api.get<ProjectAnalytics>(`/api/v1/analytics/projects${toQueryString({ topN })}`),
  collaboration: (topN?: number) => api.get<CollaborationAnalytics>(`/api/v1/analytics/collaboration${toQueryString({ topN })}`),
  activity: (query: ActivityQuery = {}) => api.get<Activity>(`/api/v1/analytics/activity${toQueryString(query)}`),
}
