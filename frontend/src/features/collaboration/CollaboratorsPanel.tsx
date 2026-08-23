import { useState, type FormEvent } from 'react'
import { useProjectShares, useCreateShare, useUpdateSharePermission, useRemoveShare } from './hooks'
import { Button } from '@/components/ui/Button'
import { Input, Label, Select } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { EmptyState } from '@/components/ui/EmptyState'
import { InlineError } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { getErrorMessage } from '@/utils/errors'
import type { ProjectRole } from '@/types/project'
import type { ProjectPermission } from '@/types/collaboration'

interface CollaboratorsPanelProps {
  projectId: string
  myRole: ProjectRole
}

// Only an OWNER ever sees the management controls here -- myRole is backend-computed (see
// ProjectResponse.myRole), never derived from local state, so there's no risk of showing a
// collaborator a control that would just fail server-side anyway. Every mutation below still
// goes through the backend's own owner-only enforcement regardless.
export function CollaboratorsPanel({ projectId, myRole }: CollaboratorsPanelProps) {
  const shares = useProjectShares(projectId)
  const createShare = useCreateShare(projectId)
  const updatePermission = useUpdateSharePermission(projectId)
  const removeShare = useRemoveShare(projectId)

  const [email, setEmail] = useState('')
  const [permission, setPermission] = useState<ProjectPermission>('VIEW')

  const isOwner = myRole === 'OWNER'

  if (!isOwner) {
    // VIEW/EDIT collaborators aren't shown the collaborator list at all -- the backend's
    // GET .../shares is itself owner-only (see ProjectShareServiceImpl), so there's nothing to
    // display here for them regardless.
    return <p className="text-sm text-ink-muted">Only the project owner can manage collaborators.</p>
  }

  function handleInvite(event: FormEvent) {
    event.preventDefault()
    createShare.mutate(
      { userEmail: email, permission },
      { onSuccess: () => setEmail('') },
    )
  }

  if (shares.isLoading) {
    return <Skeleton className="h-24 w-full" />
  }

  return (
    <div className="space-y-4">
      {shares.data && shares.data.length === 0 ? (
        <EmptyState title="This project isn't shared yet." description="Invite a collaborator by email below." />
      ) : (
        <ul className="divide-y divide-border rounded-md border border-border">
          {shares.data?.map((share) => (
            <li key={share.id} className="flex items-center justify-between gap-3 px-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-ink">{share.username}</p>
                <p className="truncate text-xs text-ink-muted">{share.email}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Select
                  aria-label={`Permission for ${share.username}`}
                  value={share.permission}
                  onChange={(e) =>
                    updatePermission.mutate({ shareId: share.id, body: { permission: e.target.value as ProjectPermission } })
                  }
                  className="h-8 w-24 py-1 text-xs"
                >
                  <option value="VIEW">VIEW</option>
                  <option value="EDIT">EDIT</option>
                </Select>
                <Button variant="ghost" size="sm" onClick={() => removeShare.mutate(share.id)}>
                  Remove
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={handleInvite} className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex-1">
          <Label htmlFor="invite-email">Invite by email</Label>
          <Input
            id="invite-email"
            type="email"
            required
            placeholder="collaborator@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div>
          <Label htmlFor="invite-permission">Permission</Label>
          <Select id="invite-permission" value={permission} onChange={(e) => setPermission(e.target.value as ProjectPermission)}>
            <option value="VIEW">VIEW</option>
            <option value="EDIT">EDIT</option>
          </Select>
        </div>
        <Button type="submit" isLoading={createShare.isPending}>
          Add collaborator
        </Button>
      </form>
      {createShare.isError && <InlineError message={getErrorMessage(createShare.error)} />}
      <Badge tone="neutral" className="w-fit">
        {shares.data?.length ?? 0} collaborator{shares.data?.length === 1 ? '' : 's'}
      </Badge>
    </div>
  )
}
