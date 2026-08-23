import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { ErrorState } from '@/components/ui/ErrorState'
import { useAnalyticsOverview, useAssetAnalytics } from '@/features/analytics/hooks'
import { ActivityChart } from '@/features/analytics/ActivityChart'
import { RankingList } from '@/features/analytics/RankingList'
import { formatBytes } from '@/utils/format'
import { getErrorMessage } from '@/utils/errors'
import { useAuth } from '@/features/auth/AuthContext'

export function DashboardPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const overview = useAnalyticsOverview()
  const assetAnalytics = useAssetAnalytics(5)

  if (overview.isError) {
    return <ErrorState message={getErrorMessage(overview.error)} onRetry={() => void overview.refetch()} />
  }

  return (
    <div>
      <PageHeader title={`Welcome back${user ? `, ${user.username}` : ''}`} description="Here's what's happening in your workspace." />

      <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <StatCard label="Assets" value={overview.data?.totalAssets ?? 0} isLoading={overview.isLoading} />
        <StatCard label="Projects" value={overview.data?.totalProjects ?? 0} isLoading={overview.isLoading} />
        <StatCard label="Collections" value={overview.data?.totalCollections ?? 0} isLoading={overview.isLoading} />
        <StatCard label="Clients" value={overview.data?.totalClients ?? 0} isLoading={overview.isLoading} />
        <StatCard label="Plays" value={overview.data?.totalPlays ?? 0} isLoading={overview.isLoading} />
        <StatCard label="Downloads" value={overview.data?.totalDownloads ?? 0} isLoading={overview.isLoading} />
      </div>

      <div className="mb-6">
        <StatCard label="Storage used" value={overview.data ? formatBytes(overview.data.totalStorageBytes) : '—'} isLoading={overview.isLoading} />
      </div>

      <div className="mb-6">
        <ActivityChart />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <RankingList
          title="Most played assets"
          isLoading={assetAnalytics.isLoading}
          entries={assetAnalytics.data?.topPlayedAssets.map((e) => ({ id: e.assetId, label: e.title, count: e.count }))}
          countLabel="plays"
          emptyMessage="No plays recorded yet."
          onEntryClick={(id) => navigate(`/assets/${id}`)}
        />
        <RankingList
          title="Most downloaded assets"
          isLoading={assetAnalytics.isLoading}
          entries={assetAnalytics.data?.topDownloadedAssets.map((e) => ({ id: e.assetId, label: e.title, count: e.count }))}
          countLabel="downloads"
          emptyMessage="No downloads recorded yet."
          onEntryClick={(id) => navigate(`/assets/${id}`)}
        />
      </div>
    </div>
  )
}
