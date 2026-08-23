import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { DEFAULT_THEME, isThemeId, type ThemeId } from './themes'

const STORAGE_KEY = 'atp-theme'

interface ThemeContextValue {
  theme: ThemeId
  setTheme: (theme: ThemeId) => void
}

const ThemeContext = createContext<ThemeContextValue>({
  theme: DEFAULT_THEME,
  setTheme: () => {},
})

function readStoredTheme(): ThemeId {
  if (typeof window === 'undefined') return DEFAULT_THEME
  const stored = window.localStorage.getItem(STORAGE_KEY)
  return isThemeId(stored) ? stored : DEFAULT_THEME
}

// Applies the chosen theme to <html data-theme="..."> (which is what index.css's [data-theme]
// blocks key off of) and persists it so it survives a reload -- a per-user preference, not tied
// to their account, so plain localStorage is enough rather than a backend field.
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<ThemeId>(readStoredTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem(STORAGE_KEY, theme)
  }, [theme])

  return <ThemeContext.Provider value={{ theme, setTheme }}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  return useContext(ThemeContext)
}
