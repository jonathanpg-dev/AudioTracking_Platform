import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useClients } from '@/features/clients/hooks'
import { ClientFormDialog } from '@/features/clients/ClientFormDialog'
import { getErrorMessage } from '@/utils/errors'

export function ClientsPage() {
  const navigate = useNavigate()
  const clients = useClients()
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <div>
      <PageHeader title="Clients" description="People and companies you work with." action={<Button onClick={() => setCreateOpen(true)}>Add Client</Button>} />

      {clients.isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      )}

      {clients.isError && <ErrorState message={getErrorMessage(clients.error)} onRetry={() => void clients.refetch()} />}

      {clients.isSuccess && clients.data.length === 0 && (
        <EmptyState
          title="You haven't added any clients yet."
          description="Clients can be linked to Projects you're working on for them."
          action={<Button onClick={() => setCreateOpen(true)}>Add Client</Button>}
        />
      )}

      {clients.isSuccess && clients.data.length > 0 && (
        <ul className="divide-y divide-border rounded-lg border border-border bg-surface">
          {clients.data.map((client) => (
            <li
              key={client.id}
              onClick={() => navigate(`/clients/${client.id}`)}
              className="flex cursor-pointer items-center justify-between px-4 py-3 text-sm hover:bg-surface-muted"
            >
              <div>
                <p className="font-medium text-ink">{client.name}</p>
                {client.company && <p className="text-ink-muted">{client.company}</p>}
              </div>
              <span className="text-ink-muted">{client.email}</span>
            </li>
          ))}
        </ul>
      )}

      <ClientFormDialog open={createOpen} onOpenChange={setCreateOpen} onSaved={(c) => navigate(`/clients/${c.id}`)} />
    </div>
  )
}
