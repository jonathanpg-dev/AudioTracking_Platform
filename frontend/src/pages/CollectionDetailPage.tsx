import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'
import {
  useCollection,
  useDeleteCollection,
  useAddAssetToCollection,
  useRemoveAssetFromCollection,
} from '@/features/collections/hooks'
import { useAssets } from '@/features/assets/hooks'
import { assetsApi } from '@/api/assets'
import { CollectionFormDialog } from '@/features/collections/CollectionFormDialog'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Input'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { formatEnumLabel } from '@/utils/format'
import { getErrorMessage, isNotFound } from '@/utils/errors'

export function CollectionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const collection = useCollection(id)
  const allAssets = useAssets()
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [assetToAdd, setAssetToAdd] = useState('')

  const deleteCollection = useDeleteCollection()
  const addAsset = useAddAssetToCollection(id ?? '')
  const removeAsset = useRemoveAssetFromCollection(id ?? '')

  // Collection only carries asset ids (see CollectionResponse.java) -- fetch each asset's full
  // detail in parallel rather than one request per row on every render.
  const assetQueries = useQueries({
    queries: (collection.data?.assetIds ?? []).map((assetId) => ({
      queryKey: ['assets', assetId],
      queryFn: () => assetsApi.get(assetId),
    })),
  })

  if (collection.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (collection.isError) {
    return (
      <ErrorState
        title={isNotFound(collection.error) ? 'Collection not found' : 'Something went wrong'}
        message={getErrorMessage(collection.error)}
      />
    )
  }

  const data = collection.data
  const availableAssets = allAssets.data?.filter((asset) => !data.assetIds.includes(asset.id)) ?? []

  return (
    <div className="max-w-3xl">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-ink">{data.name}</h1>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditOpen(true)}>
            Rename
          </Button>
          <Button variant="danger" onClick={() => setDeleteOpen(true)}>
            Delete
          </Button>
        </div>
      </div>

      <div className="mb-4 flex items-center gap-2">
        <Select value={assetToAdd} onChange={(e) => setAssetToAdd(e.target.value)} className="max-w-xs" aria-label="Add an asset to this collection">
          <option value="">Add asset to collection...</option>
          {availableAssets.map((asset) => (
            <option key={asset.id} value={asset.id}>
              {asset.title}
            </option>
          ))}
        </Select>
        <Button
          size="sm"
          variant="secondary"
          disabled={!assetToAdd}
          isLoading={addAsset.isPending}
          onClick={() => {
            addAsset.mutate(assetToAdd)
            setAssetToAdd('')
          }}
        >
          Add
        </Button>
      </div>

      {data.assetIds.length === 0 && <EmptyState title="No assets in this collection yet." />}

      {data.assetIds.length > 0 && (
        <ul className="divide-y divide-border rounded-md border border-border bg-surface">
          {assetQueries.map((query, index) => {
            const assetId = data.assetIds[index]
            if (query.isLoading) {
              return (
                <li key={assetId} className="px-4 py-3">
                  <Skeleton className="h-5 w-40" />
                </li>
              )
            }
            if (query.isError || !query.data) {
              return (
                <li key={assetId} className="px-4 py-3 text-sm text-ink-muted">
                  Asset unavailable
                </li>
              )
            }
            const asset = query.data
            return (
              <li key={assetId} className="flex items-center justify-between px-4 py-3 text-sm">
                <button onClick={() => navigate(`/assets/${asset.id}`)} className="font-medium text-ink hover:underline">
                  {asset.title}
                </button>
                <div className="flex items-center gap-3">
                  <span className="text-ink-muted">{formatEnumLabel(asset.assetType)}</span>
                  <Button variant="ghost" size="sm" onClick={() => removeAsset.mutate(asset.id)}>
                    Remove
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <CollectionFormDialog open={editOpen} onOpenChange={setEditOpen} collection={data} />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete this collection?"
        description={`"${data.name}" will be deleted. Its assets are not affected. This cannot be undone.`}
        isConfirming={deleteCollection.isPending}
        onConfirm={() => deleteCollection.mutate(data.id, { onSuccess: () => navigate('/collections') })}
      />
    </div>
  )
}
