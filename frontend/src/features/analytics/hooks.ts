import { useQuery } from '@tanstack/react-query'
import { analyticsApi } from '@/api/analytics'
import type { ActivityQuery } from '@/types/analytics'

// All analytics queries share the 'analytics' key prefix so any mutation elsewhere in the app
// can invalidate every analytics number at once with a single, broad
// invalidateQueries({queryKey: ['analytics']}) call -- see the various feature hooks.ts files.
export function useAnalyticsOverview() {
  return useQuery({ queryKey: ['analytics', 'overview'], queryFn: analyticsApi.overview })
}

export function useAssetAnalytics(topN = 5) {
  return useQuery({ queryKey: ['analytics', 'assets', topN], queryFn: () => analyticsApi.assets(topN) })
}

export function useProjectAnalytics(topN = 5) {
  return useQuery({ queryKey: ['analytics', 'projects', topN], queryFn: () => analyticsApi.projects(topN) })
}

export function useCollaborationAnalytics(topN = 5) {
  return useQuery({ queryKey: ['analytics', 'collaboration', topN], queryFn: () => analyticsApi.collaboration(topN) })
}

export function useActivity(query: ActivityQuery) {
  return useQuery({ queryKey: ['analytics', 'activity', query], queryFn: () => analyticsApi.activity(query) })
}
