import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/features/auth/AuthContext'
import { ThemeProvider } from '@/features/theme/ThemeContext'

// The same provider stack main.tsx wires up in the real app, minus BrowserRouter (MemoryRouter
// instead, so tests can control the starting URL without touching window.history).
export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  function wrap(element: ReactElement) {
    return (
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[route]}>
            <AuthProvider>{element}</AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </ThemeProvider>
    )
  }

  const result = render(wrap(ui))
  return {
    ...result,
    // Testing Library's own `rerender` replaces the whole previously-rendered tree, providers
    // included -- passing it a bare element (forgetting to re-wrap) is exactly how a test can
    // silently lose QueryClientProvider/etc. and blow up with "No QueryClient set". This wraps
    // automatically so callers just pass the next element, same as the initial render.
    rerender: (nextUi: ReactElement) => result.rerender(wrap(nextUi)),
  }
}
