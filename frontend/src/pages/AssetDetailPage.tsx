import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAsset, useDeleteAsset, useUploadAssetFile, useAddTagToAsset, useRemoveTagFromAsset, useUpdateClientNotes } from '@/features/assets/hooks'
import { useProject } from '@/features/projects/hooks'
import { useTags, useCreateTag } from '@/features/tags/hooks'
import { AudioPlayer } from '@/features/assets/AudioPlayer'
import { AssetFormDialog } from '@/features/assets/AssetFormDialog'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Select, Textarea } from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/Skeleton'
import { ErrorState, InlineError } from '@/components/ui/ErrorState'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { formatBytes, formatDateTime, formatDuration, formatEnumLabel } from '@/utils/format'
import { getErrorMessage, isNotFound } from '@/utils/errors'

export function AssetDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const asset = useAsset(id)
  // Assets outside any Project are only ever visible to their owner (full control by
  // definition); assets inside a Project defer to that Project's collaboration permission --
  // see ProjectAccessService on the backend. Only fetched when actually needed.
  const project = useProject(asset.data?.projectId ?? undefined)

  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const uploadFile = useUploadAssetFile(id ?? '')
  const deleteAsset = useDeleteAsset()
  const addTag = useAddTagToAsset(id ?? '')
  const removeTag = useRemoveTagFromAsset(id ?? '')
  const { data: allTags } = useTags()
  const createTag = useCreateTag()

  if (asset.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (asset.isError) {
    return (
      <ErrorState
        title={isNotFound(asset.error) ? 'Asset not found' : 'Something went wrong'}
        message={getErrorMessage(asset.error)}
      />
    )
  }

  const data = asset.data
  // An allow-list, not "!== 'VIEW'" -- a deny-list would silently start granting edit rights to
  // any future role (like CLIENT, which is view-only plus a narrow client-notes capability that's
  // unrelated to this) the moment it's added to ProjectRole. data.projectId === null only ever
  // loads successfully for the asset's actual owner (see AssetService#getAsset).
  const canEdit = data.projectId === null || project.data?.myRole === 'OWNER' || project.data?.myRole === 'EDIT'
  // Tags are strictly owner-only on the backend (AssetServiceImpl#addTag/removeTag look the
  // asset up scoped to the requester's own id, with no project-collaboration path at all -- see
  // the comment on findOwnedOrThrow) -- deliberately not extended to EDIT collaborators either,
  // unlike everything else gated by `canEdit` above. data.projectId === null only ever loads
  // successfully for the asset's actual owner (see AssetService#getAsset), so it's a safe proxy
  // for "not shared through a project" too.
  const canManageTags = data.projectId === null || project.data?.myRole === 'OWNER'
  // The one thing a CLIENT role *can* write -- everyone else with view+ access only ever reads
  // it (see AssetService#updateClientNotes for the backend side of this same restriction).
  const canWriteClientNotes = project.data?.myRole === 'CLIENT'
  const availableTags = allTags?.filter((tag) => !data.tags.some((t) => t.id === tag.id)) ?? []

  async function handleFileSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file || !id) return
    await uploadFile.mutateAsync(file)
    event.target.value = ''
  }

  async function handleAddExistingTag(tagId: string) {
    if (!tagId) return
    await addTag.mutateAsync(tagId)
  }

  async function handleCreateAndAddTag(name: string) {
    const tag = await createTag.mutateAsync({ name })
    await addTag.mutateAsync(tag.id)
  }

  return (
    <div className="max-w-3xl">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <p className="text-sm text-ink-muted">
            {data.projectId && data.projectName ? (
              <Link to={`/projects/${data.projectId}`} className="hover:underline">
                {data.projectName}
              </Link>
            ) : (
              'Standalone asset'
            )}
          </p>
          <h1 className="text-xl font-semibold text-ink">{data.title}</h1>
        </div>
        {canEdit && (
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

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Audio</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {/* AudioPlayer fetches its presigned URL once and caches it for the component's
              lifetime (see AudioPlayer.tsx) -- assetId/hasAudioFile alone don't change when the
              file is replaced (same asset, still has a file), so without this key the player
              would keep playing/streaming from the old file's now-invalid URL until something
              else happened to remount it. audioFormat+fileSizeBytes change on every upload
              (upload/replace/delete all update both), so keying on them forces a fresh instance
              -- and fresh state, fresh fetch on next Play -- exactly when the underlying file
              actually changes, without remounting (and losing playback position) on unrelated
              edits like the title or BPM. */}
          <AudioPlayer
            key={`${data.audioFormat ?? 'none'}-${data.fileSizeBytes ?? 0}`}
            assetId={data.id}
            hasAudioFile={data.hasAudioFile}
          />
          {canEdit && (
            <div>
              <input ref={fileInputRef} type="file" accept="audio/*" className="hidden" onChange={(e) => void handleFileSelected(e)} />
              <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()} isLoading={uploadFile.isPending}>
                {data.hasAudioFile ? 'Replace file' : 'Upload file'}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Details</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
            <Field label="Type" value={formatEnumLabel(data.assetType)} />
            <Field label="BPM" value={data.bpm?.toString() ?? '—'} />
            <Field label="Key" value={data.musicalKey ?? '—'} />
            <Field label="Duration" value={data.durationSeconds ? formatDuration(data.durationSeconds) : '—'} />
            <Field label="Format" value={data.audioFormat?.toUpperCase() ?? '—'} />
            <Field label="File size" value={data.fileSizeBytes ? formatBytes(data.fileSizeBytes) : '—'} />
            <Field label="Created" value={formatDateTime(data.createdAt)} />
            <Field label="Updated" value={formatDateTime(data.updatedAt)} />
          </dl>
          {data.description && (
            <div className="mt-4">
              <p className="text-xs font-medium uppercase text-ink-muted">Description</p>
              <p className="mt-1 text-sm text-ink">{data.description}</p>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Tags</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">
            {data.tags.length === 0 && <p className="text-sm text-ink-muted">No tags yet.</p>}
            {data.tags.map((tag) => (
              <Badge key={tag.id} tone="neutral" className="gap-1">
                {tag.name}
                {canManageTags && (
                  <button
                    type="button"
                    aria-label={`Remove tag ${tag.name}`}
                    onClick={() => removeTag.mutate(tag.id)}
                    className="ml-1 text-ink-subtle hover:text-danger"
                  >
                    ×
                  </button>
                )}
              </Badge>
            ))}
          </div>

          {canManageTags && (
            <div className="mt-3 flex items-center gap-2">
              <Select
                value=""
                onChange={(e) => void handleAddExistingTag(e.target.value)}
                className="max-w-[220px]"
                aria-label="Add an existing tag"
              >
                <option value="">Add existing tag...</option>
                {availableTags.map((tag) => (
                  <option key={tag.id} value={tag.id}>
                    {tag.name}
                  </option>
                ))}
              </Select>
              <NewTagInput onCreate={handleCreateAndAddTag} isSubmitting={createTag.isPending || addTag.isPending} />
            </div>
          )}

          {(addTag.isError || removeTag.isError || createTag.isError) && (
            <div className="mt-3">
              <InlineError
                message={getErrorMessage(addTag.error ?? removeTag.error ?? createTag.error)}
              />
            </div>
          )}
        </CardContent>
      </Card>

      {/* Hidden entirely when there's nothing to show and the viewer can't write it -- a CLIENT
          always sees the card (so they can leave a first note), everyone else only sees it once
          feedback actually exists. */}
      {(canWriteClientNotes || data.clientNotes) && (
        <ClientNotesCard assetId={data.id} clientNotes={data.clientNotes} editable={canWriteClientNotes} />
      )}

      <AssetFormDialog open={editOpen} onOpenChange={setEditOpen} asset={data} />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete this asset?"
        description={`"${data.title}" and its audio file will be permanently deleted. This cannot be undone.`}
        isConfirming={deleteAsset.isPending}
        onConfirm={() => deleteAsset.mutate(data.id, { onSuccess: () => navigate('/assets') })}
      />
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-ink">{value}</dd>
    </div>
  )
}

// Distinct from the regular Description field above -- this is feedback from the project's
// client (myRole 'CLIENT'), not the asset owner's own metadata. Editable only by that client;
// everyone else with view access sees it read-only, once it exists.
function ClientNotesCard({
  assetId,
  clientNotes,
  editable,
}: {
  assetId: string
  clientNotes: string | null
  editable: boolean
}) {
  const [draft, setDraft] = useState(clientNotes ?? '')
  const [isEditing, setIsEditing] = useState(false)
  const updateClientNotes = useUpdateClientNotes(assetId)

  async function handleSave() {
    await updateClientNotes.mutateAsync(draft.trim() === '' ? null : draft)
    setIsEditing(false)
  }

  return (
    <Card className="mb-4">
      <CardHeader>
        <CardTitle>Client Notes</CardTitle>
      </CardHeader>
      <CardContent>
        {editable ? (
          isEditing ? (
            <div className="space-y-2">
              <Textarea
                aria-label="Client notes"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                maxLength={2000}
                autoFocus
              />
              <div className="flex gap-2">
                <Button size="sm" onClick={() => void handleSave()} isLoading={updateClientNotes.isPending}>
                  Save
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    setDraft(clientNotes ?? '')
                    setIsEditing(false)
                  }}
                >
                  Cancel
                </Button>
              </div>
              {updateClientNotes.isError && <InlineError message={getErrorMessage(updateClientNotes.error)} />}
            </div>
          ) : (
            <div>
              {clientNotes ? (
                <p className="whitespace-pre-wrap text-sm text-ink">{clientNotes}</p>
              ) : (
                <p className="text-sm text-ink-muted">No notes yet -- add feedback for the project owner.</p>
              )}
              <Button size="sm" variant="secondary" className="mt-3" onClick={() => setIsEditing(true)}>
                {clientNotes ? 'Edit notes' : 'Add notes'}
              </Button>
            </div>
          )
        ) : (
          <p className="whitespace-pre-wrap text-sm text-ink">{clientNotes}</p>
        )}
      </CardContent>
    </Card>
  )
}

function NewTagInput({ onCreate, isSubmitting }: { onCreate: (name: string) => Promise<void>; isSubmitting: boolean }) {
  const [name, setName] = useState('')
  return (
    <form
      className="flex gap-2"
      onSubmit={(e) => {
        e.preventDefault()
        if (!name.trim()) return
        void onCreate(name.trim()).then(() => setName(''))
      }}
    >
      <input
        aria-label="New tag name"
        placeholder="New tag..."
        value={name}
        onChange={(e) => setName(e.target.value)}
        className="h-9 w-32 rounded-md border border-border px-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      />
      <Button type="submit" variant="secondary" size="sm" isLoading={isSubmitting}>
        Add
      </Button>
    </form>
  )
}
