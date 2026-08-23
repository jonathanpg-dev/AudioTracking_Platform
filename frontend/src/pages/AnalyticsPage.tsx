import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { useAssetAnalytics, useProjectAnalytics, useCollaborationAnalytics } from '@/features/analytics/hooks'
import { ActivityChart } from '@/features/analytics/ActivityChart'
import { RankingList } from '@/features/analytics/RankingList'

export function AnalyticsPage() {
  const navigate = useNavigate()
  const assetAnalytics = useAssetAnalytics(5)
  const projectAnalytics = useProjectAnalytics(5)
  const collaborationAnalytics = useCollaborationAnalytics(5)

  return (
    <div>
      <PageHeader title="Analytics" description="How your workspace is being used." />

      <div className="mb-6">
        <ActivityChart />
      </div>

      <section className="mb-6">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-muted">Asset activity</h2>
        <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Uploads" value={assetAnalytics.data?.totalUploads ?? 0} isLoading={assetAnalytics.isLoading} />
          <StatCard label="Plays" value={assetAnalytics.data?.totalPlays ?? 0} isLoading={assetAnalytics.isLoading} />
          <StatCard label="Downloads" value={assetAnalytics.data?.totalDownloads ?? 0} isLoading={assetAnalytics.isLoading} />
          <StatCard label="Deletions" value={assetAnalytics.data?.totalDeletions ?? 0} isLoading={assetAnalytics.isLoading} />
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
      </section>

      <section className="mb-6">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-muted">Project activity</h2>
        <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Total projects" value={projectAnalytics.data?.totalProjects ?? 0} isLoading={projectAnalytics.isLoading} />
          <StatCard label="Updates made" value={projectAnalytics.data?.totalProjectUpdates ?? 0} isLoading={projectAnalytics.isLoading} />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <RankingList
            title="Most active projects"
            isLoading={projectAnalytics.isLoading}
            entries={projectAnalytics.data?.mostActiveProjects.map((e) => ({ id: e.projectId, label: e.projectName, count: e.eventCount }))}
            countLabel="events"
            emptyMessage="No project activity yet."
            onEntryClick={(id) => navigate(`/projects/${id}`)}
          />
          <RankingList
            title="Assets per project"
            isLoading={projectAnalytics.isLoading}
            entries={projectAnalytics.data?.assetsPerProject.map((e) => ({ id: e.projectId, label: e.projectName, count: e.assetCount }))}
            countLabel="assets"
            emptyMessage="No project has assets yet."
            onEntryClick={(id) => navigate(`/projects/${id}`)}
          />
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-muted">Collaboration</h2>
        <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-3">
          <StatCard label="Projects shared" value={collaborationAnalytics.data?.totalProjectsShared ?? 0} isLoading={collaborationAnalytics.isLoading} />
          <StatCard label="Active collaborators" value={collaborationAnalytics.data?.totalActiveCollaborators ?? 0} isLoading={collaborationAnalytics.isLoading} />
          <StatCard label="Shares created" value={collaborationAnalytics.data?.totalSharesCreated ?? 0} isLoading={collaborationAnalytics.isLoading} />
        </div>
        <RankingList
          title="Most shared projects"
          isLoading={collaborationAnalytics.isLoading}
          entries={collaborationAnalytics.data?.mostSharedProjects.map((e) => ({ id: e.projectId, label: e.projectName, count: e.collaboratorCount }))}
          countLabel="collaborators"
          emptyMessage="You haven't shared any projects yet."
          onEntryClick={(id) => navigate(`/projects/${id}`)}
        />
      </section>
    </div>
  )
}
