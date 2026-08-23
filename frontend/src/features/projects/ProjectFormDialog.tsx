import { useState, type FormEvent } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Label, Select, Textarea } from '@/components/ui/Input'
import { InlineError } from '@/components/ui/ErrorState'
import { getErrorMessage } from '@/utils/errors'
import { formatEnumLabel } from '@/utils/format'
import { useCreateProject, useUpdateProject } from './hooks'
import { useClients } from '@/features/clients/hooks'
import { PROJECT_STATUSES, type Project } from '@/types/project'

interface ProjectFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  project?: Project
  onSaved?: (project: Project) => void
}

export function ProjectFormDialog({ open, onOpenChange, project, onSaved }: ProjectFormDialogProps) {
  const isEditMode = project !== undefined
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [status, setStatus] = useState(project?.status ?? 'PLANNING')
  const [clientId, setClientId] = useState(project?.clientId ?? '')

  const { data: clients } = useClients()
  const createProject = useCreateProject()
  const updateProject = useUpdateProject(project?.id ?? '')
  const mutation = isEditMode ? updateProject : createProject

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const onSuccess = (saved: Project) => {
      onOpenChange(false)
      onSaved?.(saved)
    }
    if (isEditMode) {
      updateProject.mutate({ name, description: description || null, status, clientId: clientId || null }, { onSuccess })
    } else {
      createProject.mutate({ name, description: description || null, clientId: clientId || null }, { onSuccess })
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={isEditMode ? 'Edit project' : 'New project'}>
      <form className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <Label htmlFor="project-name" required>
            Name
          </Label>
          <Input id="project-name" required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} />
        </div>

        <div>
          <Label htmlFor="project-description">Description</Label>
          <Textarea id="project-description" maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          {isEditMode && (
            <div>
              <Label htmlFor="project-status" required>
                Status
              </Label>
              <Select id="project-status" required value={status} onChange={(e) => setStatus(e.target.value as typeof status)}>
                {PROJECT_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {formatEnumLabel(s)}
                  </option>
                ))}
              </Select>
            </div>
          )}
          <div className={isEditMode ? '' : 'col-span-2'}>
            <Label htmlFor="project-client">Client</Label>
            <Select id="project-client" value={clientId} onChange={(e) => setClientId(e.target.value)}>
              <option value="">None</option>
              {clients?.map((client) => (
                <option key={client.id} value={client.id}>
                  {client.name}
                </option>
              ))}
            </Select>
          </div>
        </div>

        {mutation.isError && <InlineError message={getErrorMessage(mutation.error)} />}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={mutation.isPending}>
            {isEditMode ? 'Save changes' : 'Create project'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
