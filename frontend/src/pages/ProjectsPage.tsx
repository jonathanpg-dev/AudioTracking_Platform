import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card, CardContent } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { SortSelect } from '@/components/ui/SortSelect'
import { useProjects } from '@/features/projects/hooks'
import { ProjectFormDialog } from '@/features/projects/ProjectFormDialog'
import { formatEnumLabel } from '@/utils/format'
import { getErrorMessage } from '@/utils/errors'
import type { SortParams } from '@/types/sort'

export function ProjectsPage() {
  const navigate = useNavigate()
  const [sort, setSort] = useState<SortParams>({ sortBy: 'createdAt', sortDir: 'desc' })
  const projects = useProjects(sort)
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <div>
      <PageHeader
        title="Projects"
        description="Your creative workspaces."
        action={<Button onClick={() => setCreateOpen(true)}>Create Project</Button>}
      />

      <div className="mb-4 flex justify-end">
        <SortSelect value={sort} onChange={setSort} />
      </div>

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
          title="You haven't created a project yet."
          description="Projects group your assets, collaborators, and a client together."
          action={<Button onClick={() => setCreateOpen(true)}>Create Project</Button>}
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
                  <div className="flex shrink-0 gap-1.5">
                    {/* Only shown for shared-with-me projects -- an owner's own projects (the
                        common case) stay unbadged rather than repeating "OWNER" on every card. */}
                    {project.myRole !== 'OWNER' && <Badge tone="accent">{project.myRole}</Badge>}
                    <Badge tone="neutral">{formatEnumLabel(project.status)}</Badge>
                  </div>
                </div>
                {project.clientName && <p className="mt-1 text-sm text-ink-muted">Client: {project.clientName}</p>}
                {project.description && <p className="mt-2 line-clamp-2 text-sm text-ink-muted">{project.description}</p>}
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ProjectFormDialog open={createOpen} onOpenChange={setCreateOpen} onSaved={(p) => navigate(`/projects/${p.id}`)} />
    </div>
  )
}
