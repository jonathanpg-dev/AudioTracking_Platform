import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useClient, useDeleteClient } from '@/features/clients/hooks'
import { useProjects } from '@/features/projects/hooks'
import { ClientFormDialog } from '@/features/clients/ClientFormDialog'
import { Button } from '@/components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getErrorMessage, isNotFound } from '@/utils/errors'

export function ClientDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const client = useClient(id)
  const projects = useProjects() // no dedicated "projects for this client" endpoint; filtered client-side below
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const deleteClient = useDeleteClient()

  if (client.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  if (client.isError) {
    return (
      <ErrorState
        title={isNotFound(client.error) ? 'Client not found' : 'Something went wrong'}
        message={getErrorMessage(client.error)}
      />
    )
  }

  const data = client.data
  const relatedProjects = projects.data?.filter((project) => project.clientId === data.id) ?? []

  return (
    <div className="max-w-2xl">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-ink">{data.name}</h1>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditOpen(true)}>
            Edit
          </Button>
          <Button variant="danger" onClick={() => setDeleteOpen(true)}>
            Delete
          </Button>
        </div>
      </div>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Details</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <p>
            <span className="text-ink-muted">Email: </span>
            {data.email ?? '—'}
          </p>
          <p>
            <span className="text-ink-muted">Company: </span>
            {data.company ?? '—'}
          </p>
          {data.notes && (
            <p>
              <span className="text-ink-muted">Notes: </span>
              {data.notes}
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Projects</CardTitle>
        </CardHeader>
        <CardContent>
          {relatedProjects.length === 0 ? (
            <p className="text-sm text-ink-muted">No projects associated with this client yet.</p>
          ) : (
            <ul className="space-y-1">
              {relatedProjects.map((project) => (
                <li key={project.id}>
                  <button onClick={() => navigate(`/projects/${project.id}`)} className="text-sm font-medium text-accent hover:underline">
                    {project.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <ClientFormDialog open={editOpen} onOpenChange={setEditOpen} client={data} />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete this client?"
        description={`"${data.name}" will be deleted. Associated projects will keep their history but lose this client link. This cannot be undone.`}
        isConfirming={deleteClient.isPending}
        onConfirm={() => deleteClient.mutate(data.id, { onSuccess: () => navigate('/clients') })}
      />
    </div>
  )
}
