import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { AssetsPage } from './AssetsPage'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'

describe('AssetsPage', () => {
  it('renders assets returned by the API', async () => {
    renderWithProviders(<AssetsPage />)

    expect(await screen.findByText('Test Beat')).toBeInTheDocument()
  })

  it('shows an empty state with an upload action when there are no assets', async () => {
    server.use(http.get('http://localhost:8080/api/v1/assets', () => HttpResponse.json([])))

    renderWithProviders(<AssetsPage />)

    expect(await screen.findByText(/you don't have any assets yet/i)).toBeInTheDocument()
    // Two "Upload Asset" buttons exist (page header + empty state action) -- both are valid.
    expect(screen.getAllByRole('button', { name: /upload asset/i }).length).toBeGreaterThan(0)
  })

  it('shows an error state when the request fails', async () => {
    server.use(
      http.get('http://localhost:8080/api/v1/assets', () =>
        HttpResponse.json(
          { timestamp: '', status: 500, error: 'Internal Server Error', message: 'An unexpected error occurred', fieldErrors: null },
          { status: 500 },
        ),
      ),
    )

    renderWithProviders(<AssetsPage />)

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument()
  })

  it('sends every selected tag as a repeated tagIds param (AND match, not OR)', async () => {
    server.use(
      http.get('http://localhost:8080/api/v1/tags', () =>
        HttpResponse.json([
          { id: 'tag-1', name: 'trap', createdAt: '2026-01-01T00:00:00Z' },
          { id: 'tag-2', name: 'dark', createdAt: '2026-01-01T00:00:00Z' },
        ]),
      ),
    )
    let requestedTagIds: string[] = []
    server.use(
      http.get('http://localhost:8080/api/v1/assets', ({ request }) => {
        requestedTagIds = new URL(request.url).searchParams.getAll('tagIds')
        return HttpResponse.json([])
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<AssetsPage />)

    await user.click(await screen.findByRole('button', { name: 'trap' }))
    await user.click(await screen.findByRole('button', { name: 'dark' }))

    await waitFor(() => expect(requestedTagIds).toEqual(['tag-1', 'tag-2']))
  })

  it('sends sortBy/sortDir params matching the selected sort option', async () => {
    let requestedSort: { sortBy: string | null; sortDir: string | null } = { sortBy: null, sortDir: null }
    server.use(
      http.get('http://localhost:8080/api/v1/assets', ({ request }) => {
        const params = new URL(request.url).searchParams
        requestedSort = { sortBy: params.get('sortBy'), sortDir: params.get('sortDir') }
        return HttpResponse.json([])
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<AssetsPage />)

    await waitFor(() => expect(requestedSort).toEqual({ sortBy: 'createdAt', sortDir: 'desc' }))

    await user.selectOptions(screen.getByLabelText('Sort by'), 'Recently modified')

    await waitFor(() => expect(requestedSort).toEqual({ sortBy: 'updatedAt', sortDir: 'desc' }))
  })
})
