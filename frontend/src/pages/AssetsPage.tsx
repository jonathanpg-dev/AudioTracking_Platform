import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Input'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'
import { SortSelect } from '@/components/ui/SortSelect'
import { useAssets } from '@/features/assets/hooks'
import { useProjects } from '@/features/projects/hooks'
import { useTags } from '@/features/tags/hooks'
import { DURATION_BRACKETS } from '@/features/assets/durationBrackets'
import { AssetFormDialog } from '@/features/assets/AssetFormDialog'
import { ASSET_TYPES, type AssetType } from '@/types/asset'
import type { SortParams } from '@/types/sort'
import { formatEnumLabel } from '@/utils/format'
import { getErrorMessage } from '@/utils/errors'

const AUDIO_FORMATS = ['mp3', 'wav', 'flac', 'm4a']

const NO_BRACKET = ''

export function AssetsPage() {
  const navigate = useNavigate()
  const [assetType, setAssetType] = useState<AssetType | ''>('')
  const [projectId, setProjectId] = useState('')
  const [search, setSearch] = useState('')
  const [musicalKey, setMusicalKey] = useState('')
  const [audioFormat, setAudioFormat] = useState('')
  const [minBpm, setMinBpm] = useState('')
  const [maxBpm, setMaxBpm] = useState('')
  const [minDurationBracket, setMinDurationBracket] = useState(NO_BRACKET)
  const [maxDurationBracket, setMaxDurationBracket] = useState(NO_BRACKET)
  const [tagIds, setTagIds] = useState<string[]>([])
  const [sort, setSort] = useState<SortParams>({ sortBy: 'createdAt', sortDir: 'desc' })
  const [createOpen, setCreateOpen] = useState(false)

  const { data: projects } = useProjects()
  const { data: tags } = useTags()

  const minBracket = DURATION_BRACKETS.find((bracket) => String(bracket.index) === minDurationBracket)
  const maxBracket = DURATION_BRACKETS.find((bracket) => String(bracket.index) === maxDurationBracket)

  const assets = useAssets({
    assetType: assetType || undefined,
    projectId: projectId || undefined,
    musicalKey: musicalKey || undefined,
    audioFormat: audioFormat || undefined,
    minBpm: minBpm ? Number(minBpm) : undefined,
    maxBpm: maxBpm ? Number(maxBpm) : undefined,
    minDurationSeconds: minBracket?.minSeconds,
    maxDurationSeconds: maxBracket?.maxSeconds ?? undefined,
    tagIds: tagIds.length > 0 ? tagIds : undefined,
    sortBy: sort.sortBy,
    sortDir: sort.sortDir,
  })

  // The backend has no free-text search endpoint (see AssetFilter.java) -- filtering by title is
  // done client-side on the already-fetched, already-server-filtered page, which is reasonable
  // at this dataset size (see the spec's own allowance for this).
  const filteredAssets = useMemo(() => {
    if (!assets.data) return []
    const query = search.trim().toLowerCase()
    if (!query) return assets.data
    return assets.data.filter((asset) => asset.title.toLowerCase().includes(query))
  }, [assets.data, search])

  // The max-bracket dropdown only offers brackets at or after the selected min bracket -- picking
  // an inverted range (e.g. min "2:00" / max "1:00") would just silently return nothing, so this
  // rules it out at the UI level instead.
  const minBracketIndex = minBracket?.index ?? 0
  const availableMaxBrackets = DURATION_BRACKETS.filter((bracket) => bracket.index >= minBracketIndex)

  function handleTagToggle(tagId: string) {
    setTagIds((current) => (current.includes(tagId) ? current.filter((id) => id !== tagId) : [...current, tagId]))
  }

  return (
    <div>
      <PageHeader
        title="Assets"
        description="Every audio file in your workspace."
        action={<Button onClick={() => setCreateOpen(true)}>Upload Asset</Button>}
      />

      <div className="mb-3 flex flex-wrap gap-3">
        <Input
          placeholder="Search by title..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="sm:max-w-xs"
          aria-label="Search assets by title"
        />
        <Select value={assetType} onChange={(e) => setAssetType(e.target.value as AssetType | '')} className="sm:max-w-[180px]" aria-label="Filter by asset type">
          <option value="">All types</option>
          {ASSET_TYPES.map((type) => (
            <option key={type} value={type}>
              {formatEnumLabel(type)}
            </option>
          ))}
        </Select>
        <Select value={projectId} onChange={(e) => setProjectId(e.target.value)} className="sm:max-w-[200px]" aria-label="Filter by project">
          <option value="">All projects</option>
          {projects?.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name}
            </option>
          ))}
        </Select>
        <Input
          placeholder="Key, e.g. A minor"
          value={musicalKey}
          onChange={(e) => setMusicalKey(e.target.value)}
          className="sm:max-w-[160px]"
          aria-label="Filter by musical key"
        />
        <Select value={audioFormat} onChange={(e) => setAudioFormat(e.target.value)} className="sm:max-w-[140px]" aria-label="Filter by audio format">
          <option value="">All formats</option>
          {AUDIO_FORMATS.map((format) => (
            <option key={format} value={format}>
              {format.toUpperCase()}
            </option>
          ))}
        </Select>
      </div>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        <div className="flex items-end gap-2">
          <Input
            type="number"
            min={20}
            max={300}
            placeholder="Min BPM"
            value={minBpm}
            onChange={(e) => setMinBpm(e.target.value)}
            className="w-28"
            aria-label="Minimum BPM"
          />
          <span className="pb-2 text-sm text-ink-subtle">to</span>
          <Input
            type="number"
            min={20}
            max={300}
            placeholder="Max BPM"
            value={maxBpm}
            onChange={(e) => setMaxBpm(e.target.value)}
            className="w-28"
            aria-label="Maximum BPM"
          />
        </div>

        <div className="flex items-end gap-2">
          <Select
            value={minDurationBracket}
            onChange={(e) => {
              setMinDurationBracket(e.target.value)
              // Keep the max bracket valid if it's now below the new min.
              if (maxDurationBracket !== NO_BRACKET && Number(maxDurationBracket) < Number(e.target.value || 0)) {
                setMaxDurationBracket(e.target.value)
              }
            }}
            className="w-32"
            aria-label="Minimum duration"
          >
            <option value={NO_BRACKET}>Any duration</option>
            {DURATION_BRACKETS.map((bracket) => (
              <option key={bracket.index} value={bracket.index}>
                {bracket.label}
              </option>
            ))}
          </Select>
          <span className="pb-2 text-sm text-ink-subtle">to</span>
          <Select
            value={maxDurationBracket}
            onChange={(e) => setMaxDurationBracket(e.target.value)}
            className="w-32"
            aria-label="Maximum duration"
          >
            <option value={NO_BRACKET}>No max</option>
            {availableMaxBrackets.map((bracket) => (
              <option key={bracket.index} value={bracket.index}>
                {bracket.label}
              </option>
            ))}
          </Select>
        </div>

        <SortSelect value={sort} onChange={setSort} />
      </div>

      {tags && tags.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium uppercase tracking-wide text-ink-subtle">Tags (match all selected):</span>
          {tags.map((tag) => {
            const active = tagIds.includes(tag.id)
            return (
              <button
                key={tag.id}
                type="button"
                onClick={() => handleTagToggle(tag.id)}
                aria-pressed={active}
                className={
                  active
                    ? 'rounded-full bg-accent px-3 py-1 text-xs font-medium text-on-accent'
                    : 'rounded-full border border-border px-3 py-1 text-xs font-medium text-ink-muted hover:bg-surface-muted'
                }
              >
                {tag.name}
              </button>
            )
          })}
        </div>
      )}

      {assets.isLoading && (
        <div className="space-y-2">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      )}

      {assets.isError && <ErrorState message={getErrorMessage(assets.error)} onRetry={() => void assets.refetch()} />}

      {assets.isSuccess && filteredAssets.length === 0 && (
        <EmptyState
          title="You don't have any assets yet."
          description="Upload your first beat, sample, or stem to get started."
          action={<Button onClick={() => setCreateOpen(true)}>Upload Asset</Button>}
        />
      )}

      {assets.isSuccess && filteredAssets.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-border bg-surface">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-border bg-surface-muted text-xs uppercase text-ink-muted">
              <tr>
                <th className="px-4 py-3 font-medium">Title</th>
                <th className="px-4 py-3 font-medium">Type</th>
                <th className="px-4 py-3 font-medium">Project</th>
                <th className="px-4 py-3 font-medium">BPM / Key</th>
                <th className="px-4 py-3 font-medium">Audio</th>
              </tr>
            </thead>
            <tbody>
              {filteredAssets.map((asset) => (
                <tr
                  key={asset.id}
                  onClick={() => navigate(`/assets/${asset.id}`)}
                  className="cursor-pointer border-b border-border last:border-0 hover:bg-surface-muted"
                >
                  <td className="px-4 py-3 font-medium text-ink">{asset.title}</td>
                  <td className="px-4 py-3">
                    <Badge tone="accent">{formatEnumLabel(asset.assetType)}</Badge>
                  </td>
                  <td className="px-4 py-3 text-ink-muted">{asset.projectName ?? '—'}</td>
                  <td className="px-4 py-3 text-ink-muted">
                    {asset.bpm ?? '—'} {asset.musicalKey ?? ''}
                  </td>
                  <td className="px-4 py-3">
                    {asset.hasAudioFile ? <Badge tone="success">Uploaded</Badge> : <Badge tone="neutral">No file</Badge>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AssetFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSaved={(asset) => navigate(`/assets/${asset.id}`)}
      />
    </div>
  )
}
