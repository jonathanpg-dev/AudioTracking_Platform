import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card, CardContent } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { SortSelect } from '@/components/ui/SortSelect'
import { useCollections } from '@/features/collections/hooks'
import { CollectionFormDialog } from '@/features/collections/CollectionFormDialog'
import { getErrorMessage } from '@/utils/errors'
import type { SortParams } from '@/types/sort'

export function CollectionsPage() {
  const navigate = useNavigate()
  const [sort, setSort] = useState<SortParams>({ sortBy: 'createdAt', sortDir: 'desc' })
  const collections = useCollections(sort)
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <div>
      <PageHeader
        title="Collections"
        description="Curated groups of assets."
        action={<Button onClick={() => setCreateOpen(true)}>New Collection</Button>}
      />

      <div className="mb-4 flex justify-end">
        <SortSelect value={sort} onChange={setSort} />
      </div>

      {collections.isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
      )}

      {collections.isError && <ErrorState message={getErrorMessage(collections.error)} onRetry={() => void collections.refetch()} />}

      {collections.isSuccess && collections.data.length === 0 && (
        <EmptyState
          title="You haven't created a collection yet."
          description="Group related assets together for quick access."
          action={<Button onClick={() => setCreateOpen(true)}>New Collection</Button>}
        />
      )}

      {collections.isSuccess && collections.data.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {collections.data.map((collection) => (
            <Card
              key={collection.id}
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => navigate(`/collections/${collection.id}`)}
            >
              <CardContent>
                <h2 className="font-medium text-ink">{collection.name}</h2>
                <p className="mt-1 text-sm text-ink-muted">
                  {collection.assetIds.length} asset{collection.assetIds.length === 1 ? '' : 's'}
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <CollectionFormDialog open={createOpen} onOpenChange={setCreateOpen} onSaved={(c) => navigate(`/collections/${c.id}`)} />
    </div>
  )
}
