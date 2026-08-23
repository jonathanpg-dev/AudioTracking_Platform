import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import { useAuth } from '@/features/auth/AuthContext'

export function UserMenu() {
  const { user, signOut } = useAuth()

  if (!user) return null

  const initial = user.username.charAt(0).toUpperCase()

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          className="flex items-center gap-2 rounded-full py-1 pl-1 pr-2 text-sm hover:bg-surface-muted focus-visible:outline-none"
          aria-label={`Account menu for ${user.username}`}
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-accent text-sm font-semibold text-on-accent">
            {initial}
          </span>
          <span className="hidden text-ink sm:inline">{user.username}</span>
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={8}
          className="z-50 w-56 rounded-md border border-border bg-surface p-1 shadow-lg"
        >
          <div className="px-3 py-2">
            <p className="truncate text-sm font-medium text-ink">{user.username}</p>
            <p className="truncate text-xs text-ink-muted">{user.email}</p>
          </div>
          <DropdownMenu.Separator className="my-1 h-px bg-border" />
          <DropdownMenu.Item
            onSelect={signOut}
            className="cursor-pointer rounded-md px-3 py-2 text-sm text-ink outline-none hover:bg-surface-muted focus:bg-surface-muted"
          >
            Log out
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
