import { NavLink, useNavigate } from 'react-router-dom'
import { cn } from '@/utils/cn'
import { getNavItems } from './navItems'
import { useAuth } from '@/features/auth/AuthContext'
import { useUnlockCreatorMode } from '@/features/auth/hooks'
import { InlineError } from '@/components/ui/ErrorState'
import { getErrorMessage } from '@/utils/errors'

export function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth()
  // user is never null here -- NavLinks only ever renders inside AppLayout, which itself only
  // ever renders behind ProtectedRoute. Falling back to the full nav's shape is just defensive.
  const items = getNavItems({ isClientOnly: user?.isClientOnly ?? false, isLinkedAsClient: user?.isLinkedAsClient ?? false })

  return (
    <nav className="flex flex-col gap-1" aria-label="Main">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'rounded-md px-3 py-2 text-sm font-medium transition-colors',
              isActive ? 'bg-accent-soft text-accent' : 'text-ink-muted hover:bg-surface-muted hover:text-ink',
            )
          }
        >
          {item.label}
        </NavLink>
      ))}
      {user?.isClientOnly && <BecomeCreatorButton onNavigate={onNavigate} />}
    </nav>
  )
}

// Only ever rendered for a client-only account (see the isClientOnly check above) -- unlocks the
// full UI on demand, without requiring it to have created anything of its own first. Once it
// succeeds, isClientOnly flips to false server-side and the invalidated /users/me refetch makes
// this button disappear on its own (getNavItems above switches to the full nav on the next
// render) -- no local "hide myself" state needed.
function BecomeCreatorButton({ onNavigate }: { onNavigate?: () => void }) {
  const navigate = useNavigate()
  const unlockCreatorMode = useUnlockCreatorMode()

  function handleClick() {
    unlockCreatorMode.mutate(undefined, {
      onSuccess: () => {
        onNavigate?.()
        navigate('/dashboard')
      },
    })
  }

  return (
    <div className="mt-1 border-t border-border pt-1">
      <button
        type="button"
        onClick={handleClick}
        disabled={unlockCreatorMode.isPending}
        className={cn(
          'w-full rounded-md border border-dashed border-border px-3 py-2 text-left text-sm font-medium text-ink-muted transition-colors',
          'hover:border-accent hover:text-accent disabled:cursor-not-allowed disabled:opacity-50',
        )}
      >
        {unlockCreatorMode.isPending ? 'Unlocking…' : 'Become a creator too'}
      </button>
      {unlockCreatorMode.isError && (
        <div className="mt-2">
          <InlineError message={getErrorMessage(unlockCreatorMode.error)} />
        </div>
      )}
    </div>
  )
}
