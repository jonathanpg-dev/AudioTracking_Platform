import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { AssetDetailPage } from './AssetDetailPage'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'
import { mockAsset, mockProject } from '@/test/handlers'

const BASE = 'http://localhost:8080'

function TestApp() {
  return (
    <Routes>
      <Route path="/assets/:id" element={<AssetDetailPage />} />
    </Routes>
  )
}

// Regression coverage for a real bug: tags are strictly owner-only on the backend (see
// AssetServiceImpl#addTag/removeTag), not even extended to EDIT collaborators -- but the Tags
// section had no permission gating at all on the frontend, showing fully interactive "Add
// existing tag"/"New tag"/remove controls to a VIEW collaborator. The backend correctly rejected
// the actual attach (404), but the UI gave no error feedback and silently created a dangling tag
// as a side effect -- easy to mistake for "I was able to modify it".
describe('AssetDetailPage tag management gating', () => {
  it('hides tag-management controls from a VIEW collaborator', async () => {
    const sharedAsset = { ...mockAsset, id: 'asset-1', projectId: 'project-1', projectName: 'Test Project', tags: [{ id: 'tag-1', name: 'trap', createdAt: '2026-01-01T00:00:00Z' }] }
    const viewProject = { ...mockProject, id: 'project-1', myRole: 'VIEW' as const }
    server.use(
      http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(sharedAsset)),
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(viewProject)),
    )

    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    expect(screen.queryByLabelText(/add an existing tag/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/new tag name/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /remove tag trap/i })).not.toBeInTheDocument()
    // The tag itself is still visible -- VIEW means read-only, not hidden.
    expect(screen.getByText('trap')).toBeInTheDocument()
  })

  it('shows tag-management controls to the project owner', async () => {
    const sharedAsset = { ...mockAsset, id: 'asset-1', projectId: 'project-1', projectName: 'Test Project', tags: [{ id: 'tag-1', name: 'trap', createdAt: '2026-01-01T00:00:00Z' }] }
    const ownerProject = { ...mockProject, id: 'project-1', myRole: 'OWNER' as const }
    server.use(
      http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(sharedAsset)),
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(ownerProject)),
    )

    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    expect(await screen.findByLabelText(/add an existing tag/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/new tag name/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /remove tag trap/i })).toBeInTheDocument()
  })

  it('shows tag-management controls for a standalone (unshared) asset', async () => {
    const standaloneAsset = { ...mockAsset, id: 'asset-1', projectId: null, projectName: null }
    server.use(http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(standaloneAsset)))

    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    expect(await screen.findByLabelText(/add an existing tag/i)).toBeInTheDocument()
  })
})

describe('AssetDetailPage client notes', () => {
  it('hides the Client Notes card entirely when there are no notes and the viewer is not the client', async () => {
    const sharedAsset = { ...mockAsset, id: 'asset-1', projectId: 'project-1', projectName: 'Test Project', clientNotes: null }
    const ownerProject = { ...mockProject, id: 'project-1', myRole: 'OWNER' as const }
    server.use(
      http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(sharedAsset)),
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(ownerProject)),
    )

    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    expect(screen.queryByText('Client Notes')).not.toBeInTheDocument()
  })

  it('shows existing client notes read-only to the project owner', async () => {
    const sharedAsset = { ...mockAsset, id: 'asset-1', projectId: 'project-1', projectName: 'Test Project', clientNotes: 'Love the drop at 0:45!' }
    const ownerProject = { ...mockProject, id: 'project-1', myRole: 'OWNER' as const }
    server.use(
      http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(sharedAsset)),
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(ownerProject)),
    )

    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    expect(await screen.findByText('Love the drop at 0:45!')).toBeInTheDocument()
    // Read-only for the owner -- CLIENT is the only role that can write client notes.
    expect(screen.queryByLabelText(/client notes/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /edit notes/i })).not.toBeInTheDocument()
  })

  it('lets the linked CLIENT write and save client notes', async () => {
    const sharedAsset = { ...mockAsset, id: 'asset-1', projectId: 'project-1', projectName: 'Test Project', clientNotes: null }
    const clientProject = { ...mockProject, id: 'project-1', myRole: 'CLIENT' as const }
    let requestBody: unknown
    server.use(
      http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(sharedAsset)),
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(clientProject)),
      http.put(`${BASE}/api/v1/assets/asset-1/client-notes`, async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json({ ...sharedAsset, clientNotes: (requestBody as { clientNotes: string }).clientNotes })
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    await user.click(await screen.findByRole('button', { name: /add notes/i }))
    await user.type(screen.getByLabelText(/client notes/i), 'Can we make the vocals louder?')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(requestBody).toEqual({ clientNotes: 'Can we make the vocals louder?' }))
    expect(await screen.findByText('Can we make the vocals louder?')).toBeInTheDocument()
  })

  it('does not let a VIEW collaborator write client notes, even though they can read existing ones', async () => {
    const sharedAsset = { ...mockAsset, id: 'asset-1', projectId: 'project-1', projectName: 'Test Project', clientNotes: 'Needs a longer intro' }
    const viewProject = { ...mockProject, id: 'project-1', myRole: 'VIEW' as const }
    server.use(
      http.get(`${BASE}/api/v1/assets/asset-1`, () => HttpResponse.json(sharedAsset)),
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(viewProject)),
    )

    renderWithProviders(<TestApp />, { route: '/assets/asset-1' })

    await screen.findByText(mockAsset.title)
    expect(await screen.findByText('Needs a longer intro')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /edit notes/i })).not.toBeInTheDocument()
  })
})
