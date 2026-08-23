import { Dialog } from './Dialog'
import { Button } from './Button'

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel?: string
  isConfirming?: boolean
  destructive?: boolean
  onConfirm: () => void
}

// The one confirmation dialog used everywhere something gets deleted/removed (Asset, Project,
// Client, Collection, a collaborator's share) -- consistent copy pattern and button placement
// instead of a bespoke window.confirm() or one-off modal per feature.
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Delete',
  isConfirming,
  destructive = true,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title} description={description}>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={isConfirming}>
          Cancel
        </Button>
        <Button variant={destructive ? 'danger' : 'primary'} onClick={onConfirm} isLoading={isConfirming}>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  )
}
