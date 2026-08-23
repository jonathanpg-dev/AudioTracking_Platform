import { useState, type FormEvent } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Label, Select, Textarea } from '@/components/ui/Input'
import { DurationInput } from '@/components/ui/DurationInput'
import { InlineError } from '@/components/ui/ErrorState'
import { getErrorMessage } from '@/utils/errors'
import { formatEnumLabel } from '@/utils/format'
import { useCreateAsset, useUpdateAsset } from './hooks'
import { useProjects } from '@/features/projects/hooks'
import { ASSET_TYPES, type Asset, type CreateAssetRequest } from '@/types/asset'

interface AssetFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  asset?: Asset // present -> edit mode, absent -> create mode
  defaultProjectId?: string
  onSaved?: (asset: Asset) => void
}

export function AssetFormDialog({ open, onOpenChange, asset, defaultProjectId, onSaved }: AssetFormDialogProps) {
  const isEditMode = asset !== undefined
  const [title, setTitle] = useState(asset?.title ?? '')
  const [assetType, setAssetType] = useState(asset?.assetType ?? 'BEAT')
  const [description, setDescription] = useState(asset?.description ?? '')
  const [bpm, setBpm] = useState(asset?.bpm?.toString() ?? '')
  const [musicalKey, setMusicalKey] = useState(asset?.musicalKey ?? '')
  const [durationSeconds, setDurationSeconds] = useState<number | null>(asset?.durationSeconds ?? null)
  const [projectId, setProjectId] = useState(asset?.projectId ?? defaultProjectId ?? '')

  const { data: projects } = useProjects()
  const createAsset = useCreateAsset()
  const updateAsset = useUpdateAsset(asset?.id ?? '')
  const mutation = isEditMode ? updateAsset : createAsset

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const body: CreateAssetRequest = {
      title,
      assetType,
      description: description || null,
      bpm: bpm ? Number(bpm) : null,
      musicalKey: musicalKey || null,
      durationSeconds,
      audioFormat: asset?.audioFormat ?? null,
      fileSizeBytes: asset?.fileSizeBytes ?? null,
      projectId: projectId || null,
    }
    mutation.mutate(body, {
      onSuccess: (saved) => {
        onOpenChange(false)
        onSaved?.(saved)
      },
    })
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={isEditMode ? 'Edit asset' : 'New asset'}
      description={isEditMode ? undefined : 'Create the asset first, then upload its audio file.'}
    >
      <form className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <Label htmlFor="asset-title" required>
            Title
          </Label>
          <Input id="asset-title" required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="asset-type" required>
              Type
            </Label>
            <Select id="asset-type" required value={assetType} onChange={(e) => setAssetType(e.target.value as typeof assetType)}>
              {ASSET_TYPES.map((type) => (
                <option key={type} value={type}>
                  {formatEnumLabel(type)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Label htmlFor="asset-project">Project</Label>
            <Select id="asset-project" value={projectId} onChange={(e) => setProjectId(e.target.value)}>
              <option value="">None</option>
              {projects?.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div>
          <Label htmlFor="asset-description">Description</Label>
          <Textarea id="asset-description" maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="asset-bpm">BPM</Label>
            <Input id="asset-bpm" type="number" min={20} max={300} value={bpm} onChange={(e) => setBpm(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="asset-key">Key</Label>
            <Input id="asset-key" maxLength={30} placeholder="e.g. A minor" value={musicalKey} onChange={(e) => setMusicalKey(e.target.value)} />
          </div>
        </div>

        <div>
          <Label htmlFor="asset-duration">Duration</Label>
          <DurationInput id="asset-duration" value={durationSeconds} onChange={setDurationSeconds} />
          <p className="mt-1 text-xs text-ink-subtle">Hours : Minutes : Seconds</p>
        </div>

        {mutation.isError && <InlineError message={getErrorMessage(mutation.error)} />}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={mutation.isPending}>
            {isEditMode ? 'Save changes' : 'Create asset'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
