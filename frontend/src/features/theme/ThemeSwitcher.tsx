import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import { useTheme } from './ThemeContext'
import { THEMES } from './themes'

// A small trigger + dropdown, same Radix pattern as layouts/UserMenu -- used both in the
// authenticated app header and on the public login/register pages, so it can't depend on
// AuthContext.
export function ThemeSwitcher() {
  const { theme, setTheme } = useTheme()
  const active = THEMES.find((candidate) => candidate.id === theme) ?? THEMES[0]

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          className="flex h-9 w-9 items-center justify-center rounded-full border border-border text-ink-muted hover:bg-surface-muted hover:text-ink focus-visible:outline-none"
          aria-label={`Theme: ${active.label}. Change theme`}
        >
          <PaletteIcon />
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={8}
          className="z-50 w-64 rounded-md border border-border bg-surface p-1 shadow-lg"
        >
          <div className="px-3 py-2 text-xs font-medium uppercase tracking-wide text-ink-subtle">
            Theme
          </div>
          {THEMES.map((candidate) => (
            <DropdownMenu.Item
              key={candidate.id}
              onSelect={() => setTheme(candidate.id)}
              className="flex cursor-pointer items-center gap-3 rounded-md px-3 py-2 text-sm text-ink outline-none hover:bg-surface-muted focus:bg-surface-muted"
            >
              <span
                className="flex h-6 w-6 shrink-0 overflow-hidden rounded-full border border-border"
                aria-hidden="true"
              >
                <span className="h-full w-1/2" style={{ backgroundColor: candidate.swatch[0] }} />
                <span className="h-full w-1/2" style={{ backgroundColor: candidate.swatch[1] }} />
              </span>
              <span className="flex-1">
                <span className="block font-medium">{candidate.label}</span>
                <span className="block text-xs text-ink-muted">{candidate.description}</span>
              </span>
              {candidate.id === theme && (
                <CheckIcon className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
              )}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

function PaletteIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <path
        d="M10 2.5a7.5 7.5 0 1 0 0 15c.83 0 1.5-.67 1.5-1.5 0-.4-.16-.76-.41-1.03a1.5 1.5 0 0 1 1.09-2.53H13.5A3.5 3.5 0 0 0 17 9c0-3.59-3.13-6.5-7-6.5Z"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <circle cx="6.25" cy="9" r="1" fill="currentColor" />
      <circle cx="8.75" cy="6" r="1" fill="currentColor" />
      <circle cx="12" cy="6.75" r="1" fill="currentColor" />
    </svg>
  )
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <path d="M4 10.5l3.5 3.5L16 5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
