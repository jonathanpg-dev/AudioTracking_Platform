import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { Card, CardContent } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useProjectsAsClient } from '@/features/projects/hooks'
import { formatEnumLabel } from '@/utils/format'
import { getErrorMessage } from '@/utils/errors'

// Projects where the signed-in account is the linked client rather than the owner or a
// collaborator -- a deliberately separate page/list from ProjectsPage (see
// GET /projects/as-client on the backend). Reached two ways: it's the *only* page a client-only
// account (isClientOnly) has, and it's an extra nav item for a dual-role account that also owns
// its own work. Either way, opening a project here lands on the same ProjectDetailPage everyone
// else uses -- myRole 'CLIENT' already makes it render view-only there, with the client notes
// card as the one thing this account can write.
export function ClientProjectsPage() {
  const navigate = useNavigate()
  const projects = useProjectsAsClient({ sortBy: 'createdAt', sortDir: 'desc' })

  return (
    <div>
      <PageHeader title="Client Projects" description="Projects a producer has shared with you for feedback." />

      {projects.isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-32 w-full" />
          ))}
        </div>
      )}

      {projects.isError && <ErrorState message={getErrorMessage(projects.error)} onRetry={() => void projects.refetch()} />}

      {projects.isSuccess && projects.data.length === 0 && (
        <EmptyState
          title="No projects shared with you yet."
          description="When a producer adds you as a client on a project, it will show up here."
        />
      )}

      {projects.isSuccess && projects.data.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {projects.data.map((project) => (
            <Card
              key={project.id}
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => navigate(`/projects/${project.id}`)}
            >
              <CardContent>
                <div className="flex items-start justify-between gap-2">
                  <h2 className="font-medium text-ink">{project.name}</h2>
                  <Badge tone="neutral">{formatEnumLabel(project.status)}</Badge>
                </div>
                {project.description && <p className="mt-2 line-clamp-2 text-sm text-ink-muted">{project.description}</p>}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
