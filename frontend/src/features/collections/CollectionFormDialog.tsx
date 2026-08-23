import { useState, type FormEvent } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Label } from '@/components/ui/Input'
import { InlineError } from '@/components/ui/ErrorState'
import { getErrorMessage } from '@/utils/errors'
import { useCreateCollection, useUpdateCollection } from './hooks'
import type { Collection } from '@/types/collection'

interface CollectionFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  collection?: Collection
  onSaved?: (collection: Collection) => void
}

export function CollectionFormDialog({ open, onOpenChange, collection, onSaved }: CollectionFormDialogProps) {
  const isEditMode = collection !== undefined
  const [name, setName] = useState(collection?.name ?? '')

  const createCollection = useCreateCollection()
  const updateCollection = useUpdateCollection(collection?.id ?? '')
  const mutation = isEditMode ? updateCollection : createCollection

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate(
      { name },
      {
        onSuccess: (saved) => {
          onOpenChange(false)
          onSaved?.(saved)
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={isEditMode ? 'Rename collection' : 'New collection'}>
      <form className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <Label htmlFor="collection-name" required>
            Name
          </Label>
          <Input id="collection-name" required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} />
        </div>

        {mutation.isError && <InlineError message={getErrorMessage(mutation.error)} />}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={mutation.isPending}>
            {isEditMode ? 'Save changes' : 'Create collection'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
