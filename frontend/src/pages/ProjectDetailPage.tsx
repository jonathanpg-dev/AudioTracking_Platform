import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import * as Tabs from '@radix-ui/react-tabs'
import { useProject, useDeleteProject } from '@/features/projects/hooks'
import { useProjectAssets, useAssets, useAssignAssetToProject } from '@/features/assets/hooks'
import { ProjectFormDialog } from '@/features/projects/ProjectFormDialog'
import { CollaboratorsPanel } from '@/features/collaboration/CollaboratorsPanel'
import { AssetFormDialog } from '@/features/assets/AssetFormDialog'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Input'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { cn } from '@/utils/cn'
import { formatEnumLabel } from '@/utils/format'
import { getErrorMessage, isNotFound } from '@/utils/errors'

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const project = useProject(id)
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [addAssetOpen, setAddAssetOpen] = useState(false)
  const deleteProject = useDeleteProject()

  if (project.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (project.isError) {
    return (
      <ErrorState
        title={isNotFound(project.error) ? 'Project not found' : "You don't have permission"}
        message={getErrorMessage(project.error)}
      />
    )
  }

  const data = project.data
  const isOwner = data.myRole === 'OWNER'
  // An allow-list, not "!== 'VIEW'" -- a deny-list would silently start granting edit rights to
  // any future role (like CLIENT, which is view-only plus a narrow client-notes capability
  // that's unrelated to this) the moment it's added to ProjectRole.
  const canEditAssets = data.myRole === 'OWNER' || data.myRole === 'EDIT'

  return (
    <div className="max-w-4xl">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold text-ink">{data.name}</h1>
            <Badge tone="neutral">{formatEnumLabel(data.status)}</Badge>
            <Badge tone="accent">{data.myRole}</Badge>
          </div>
          {data.clientName && <p className="mt-1 text-sm text-ink-muted">Client: {data.clientName}</p>}
          {data.description && <p className="mt-2 max-w-xl text-sm text-ink-muted">{data.description}</p>}
        </div>
        {/* Only the OWNER may edit administrative Project info or delete it -- myRole is
            backend-computed, this is purely a UX convenience; the backend enforces this
            regardless of what the UI shows. */}
        {isOwner && (
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setEditOpen(true)}>
              Edit
            </Button>
            <Button variant="danger" onClick={() => setDeleteOpen(true)}>
              Delete
            </Button>
          </div>
        )}
      </div>

      <Tabs.Root defaultValue="assets">
        <Tabs.List className="mb-4 flex gap-1 border-b border-border" aria-label="Project sections">
          <TabTrigger value="assets">Assets</TabTrigger>
          <TabTrigger value="collaborators">Collaborators</TabTrigger>
        </Tabs.List>

        <Tabs.Content value="assets">
          <ProjectAssetsTab projectId={data.id} canEdit={canEditAssets} addAssetOpen={addAssetOpen} setAddAssetOpen={setAddAssetOpen} />
        </Tabs.Content>

        <Tabs.Content value="collaborators">
          <CollaboratorsPanel projectId={data.id} myRole={data.myRole} />
        </Tabs.Content>
      </Tabs.Root>

      <ProjectFormDialog open={editOpen} onOpenChange={setEditOpen} project={data} />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete this project?"
        description={`"${data.name}" will be deleted. Its assets will survive, unassigned. Collaborator access will be revoked. This cannot be undone.`}
        isConfirming={deleteProject.isPending}
        onConfirm={() => deleteProject.mutate(data.id, { onSuccess: () => navigate('/projects') })}
      />
    </div>
  )
}

function TabTrigger({ value, children }: { value: string; children: string }) {
  return (
    <Tabs.Trigger
      value={value}
      className={cn(
        'border-b-2 border-transparent px-3 py-2 text-sm font-medium text-ink-muted transition-colors',
        'hover:text-ink data-[state=active]:border-accent data-[state=active]:text-accent',
      )}
    >
      {children}
    </Tabs.Trigger>
  )
}

function ProjectAssetsTab({
  projectId,
  canEdit,
  addAssetOpen,
  setAddAssetOpen,
}: {
  projectId: string
  canEdit: boolean
  addAssetOpen: boolean
  setAddAssetOpen: (open: boolean) => void
}) {
  const navigate = useNavigate()
  const assets = useProjectAssets(projectId)
  const allAssets = useAssets()
  const assignAsset = useAssignAssetToProject()
  const [assetToAdd, setAssetToAdd] = useState('')

  // Anything not already in *this* project -- including an asset that belongs to a different
  // project, which this reassigns (an asset can only ever be in one project at a time). The
  // label makes that explicit rather than silently "stealing" it from its current project.
  const availableAssets = allAssets.data?.filter((asset) => asset.projectId !== projectId) ?? []

  function handleAddExisting() {
    const asset = allAssets.data?.find((candidate) => candidate.id === assetToAdd)
    if (!asset) return
    assignAsset.mutate({ asset, projectId })
    setAssetToAdd('')
  }

  return (
    <div>
      {canEdit && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Select
              value={assetToAdd}
              onChange={(e) => setAssetToAdd(e.target.value)}
              className="max-w-xs"
              aria-label="Add an existing asset to this project"
            >
              <option value="">Add existing asset...</option>
              {availableAssets.map((asset) => (
                <option key={asset.id} value={asset.id}>
                  {asset.title}
                  {asset.projectName ? ` (currently in ${asset.projectName})` : ''}
                </option>
              ))}
            </Select>
            <Button size="sm" variant="secondary" disabled={!assetToAdd} isLoading={assignAsset.isPending} onClick={handleAddExisting}>
              Add
            </Button>
          </div>
          <Button size="sm" onClick={() => setAddAssetOpen(true)}>
            New Asset
          </Button>
        </div>
      )}

      {assignAsset.isError && <ErrorState message={getErrorMessage(assignAsset.error)} />}

      {assets.isLoading && <Skeleton className="h-24 w-full" />}
      {assets.isError && <ErrorState message={getErrorMessage(assets.error)} />}
      {assets.isSuccess && assets.data.length === 0 && (
        <EmptyState title="No assets in this project yet." action={canEdit ? <Button onClick={() => setAddAssetOpen(true)}>New Asset</Button> : undefined} />
      )}
      {assets.isSuccess && assets.data.length > 0 && (
        <ul className="divide-y divide-border rounded-md border border-border bg-surface">
          {assets.data.map((asset) => (
            <li key={asset.id} className="flex items-center justify-between px-4 py-3 text-sm hover:bg-surface-muted">
              <button onClick={() => navigate(`/assets/${asset.id}`)} className="font-medium text-ink hover:underline">
                {asset.title}
              </button>
              <div className="flex items-center gap-3">
                <span className="text-ink-muted">{formatEnumLabel(asset.assetType)}</span>
                {canEdit && (
                  <Button
                    variant="ghost"
                    size="sm"
                    isLoading={assignAsset.isPending}
                    onClick={() => assignAsset.mutate({ asset, projectId: null })}
                  >
                    Remove
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      <AssetFormDialog open={addAssetOpen} onOpenChange={setAddAssetOpen} defaultProjectId={projectId} />
    </div>
  )
}
