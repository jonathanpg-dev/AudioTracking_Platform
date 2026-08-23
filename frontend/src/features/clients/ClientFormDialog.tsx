import { useState, type FormEvent } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Label, Textarea } from '@/components/ui/Input'
import { InlineError } from '@/components/ui/ErrorState'
import { getErrorMessage } from '@/utils/errors'
import { useCreateClient, useUpdateClient } from './hooks'
import type { Client } from '@/types/client'

interface ClientFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  client?: Client
  onSaved?: (client: Client) => void
}

export function ClientFormDialog({ open, onOpenChange, client, onSaved }: ClientFormDialogProps) {
  const isEditMode = client !== undefined
  const [name, setName] = useState(client?.name ?? '')
  const [email, setEmail] = useState(client?.email ?? '')
  const [company, setCompany] = useState(client?.company ?? '')
  const [notes, setNotes] = useState(client?.notes ?? '')

  const createClient = useCreateClient()
  const updateClient = useUpdateClient(client?.id ?? '')
  const mutation = isEditMode ? updateClient : createClient

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate(
      { name, email: email || null, company: company || null, notes: notes || null },
      {
        onSuccess: (saved) => {
          onOpenChange(false)
          onSaved?.(saved)
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={isEditMode ? 'Edit client' : 'New client'}>
      <form className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <Label htmlFor="client-name" required>
            Name
          </Label>
          <Input id="client-name" required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="client-email">Email</Label>
          <Input id="client-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="client-company">Company</Label>
          <Input id="client-company" maxLength={150} value={company} onChange={(e) => setCompany(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="client-notes">Notes</Label>
          <Textarea id="client-notes" maxLength={2000} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>

        {mutation.isError && <InlineError message={getErrorMessage(mutation.error)} />}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={mutation.isPending}>
            {isEditMode ? 'Save changes' : 'Add client'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
