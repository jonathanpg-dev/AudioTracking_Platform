// The 5 selectable themes. `id` is written straight to <html data-theme="..."> and to
// localStorage, so treat these strings as a stable contract once shipped -- see index.css for
// the actual [data-theme="..."] token overrides each one drives, and ThemeContext for how the
// active one is applied and persisted. Also mirrored in index.html's no-flash inline script --
// keep that list in sync when adding/removing a theme.
export const THEMES = [
  {
    id: 'biophilic',
    label: 'Biophilic Cyberpunk',
    description: 'Deep forest shadow, bioluminescent green + mycelium gold.',
    swatch: ['#0d1410', '#39ff8a'],
  },
  {
    id: 'light',
    label: 'Light Modern',
    description: 'Clean and bright, one bold accent.',
    swatch: ['#ffffff', '#7c3aed'],
  },
  {
    id: 'dark',
    label: 'Studio Dark',
    description: 'Mixing-console dark, electric violet.',
    swatch: ['#17141f', '#a78bfa'],
  },
  {
    id: 'analog',
    label: 'Warm Analog',
    description: 'Tape and vinyl, amber accent.',
    swatch: ['#1e1811', '#f0a04b'],
  },
  {
    id: 'gradient',
    label: 'Bold Gradient',
    description: 'Vivid indigo, pink/violet gradient.',
    swatch: ['#171130', '#ec4899'],
  },
] as const

export type ThemeId = (typeof THEMES)[number]['id']

export const DEFAULT_THEME: ThemeId = 'biophilic'

export function isThemeId(value: string | null): value is ThemeId {
  return THEMES.some((theme) => theme.id === value)
}
