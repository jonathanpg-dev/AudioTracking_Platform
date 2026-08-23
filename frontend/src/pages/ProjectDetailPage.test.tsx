import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { ProjectDetailPage } from './ProjectDetailPage'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/mswServer'
import { mockAsset, mockProject } from '@/test/handlers'

const BASE = 'http://localhost:8080'

function TestApp() {
  return (
    <Routes>
      <Route path="/projects/:id" element={<ProjectDetailPage />} />
    </Routes>
  )
}

describe('ProjectDetailPage assets tab', () => {
  it('adds an existing (currently standalone) asset to the project via a full-replace PUT', async () => {
    const standaloneAsset = { ...mockAsset, id: 'asset-2', title: 'Standalone Beat', projectId: null, projectName: null }
    server.use(
      http.get(`${BASE}/api/v1/projects/project-1/assets`, () => HttpResponse.json([])), // nothing in the project yet
      http.get(`${BASE}/api/v1/assets`, () => HttpResponse.json([standaloneAsset])),
    )
    let requestBody: unknown
    server.use(
      http.put(`${BASE}/api/v1/assets/asset-2`, async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json({ ...standaloneAsset, projectId: 'project-1', projectName: 'Test Project' })
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<TestApp />, { route: '/projects/project-1' })

    await screen.findByText(mockProject.name)
    await screen.findByRole('option', { name: 'Standalone Beat' }) // wait for useAssets() to resolve
    await user.selectOptions(screen.getByLabelText(/add an existing asset/i), 'asset-2')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    await waitFor(() =>
      expect(requestBody).toMatchObject({
        title: 'Standalone Beat',
        assetType: standaloneAsset.assetType,
        projectId: 'project-1',
      }),
    )
  })

  it("shows an asset's current project in the picker instead of silently offering to steal it", async () => {
    const elsewhereAsset = { ...mockAsset, id: 'asset-3', title: 'Borrowed Beat', projectId: 'project-2', projectName: 'Other Project' }
    server.use(
      http.get(`${BASE}/api/v1/projects/project-1/assets`, () => HttpResponse.json([])),
      http.get(`${BASE}/api/v1/assets`, () => HttpResponse.json([elsewhereAsset])),
    )

    renderWithProviders(<TestApp />, { route: '/projects/project-1' })

    await screen.findByText(mockProject.name)
    expect(await screen.findByText(/Borrowed Beat \(currently in Other Project\)/)).toBeInTheDocument()
  })

  it('removes an asset from the project by sending projectId: null', async () => {
    const inProjectAsset = { ...mockAsset, id: 'asset-1', title: 'In Project Beat', projectId: 'project-1', projectName: 'Test Project' }
    server.use(
      http.get(`${BASE}/api/v1/projects/project-1/assets`, () => HttpResponse.json([inProjectAsset])),
      http.get(`${BASE}/api/v1/assets`, () => HttpResponse.json([inProjectAsset])),
    )
    let requestBody: unknown
    server.use(
      http.put(`${BASE}/api/v1/assets/asset-1`, async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json({ ...inProjectAsset, projectId: null, projectName: null })
      }),
    )

    const user = userEvent.setup()
    renderWithProviders(<TestApp />, { route: '/projects/project-1' })

    await user.click(await screen.findByRole('button', { name: /remove/i }))

    await waitFor(() => expect(requestBody).toMatchObject({ title: 'In Project Beat', projectId: null }))
  })
})

// Regression coverage: canEditAssets used to be `myRole !== 'VIEW'`, a deny-list that would have
// silently granted a CLIENT viewer the same asset-management controls as an OWNER/EDIT
// collaborator the moment the CLIENT role was introduced. See the allow-list fix on
// ProjectDetailPage.
describe('ProjectDetailPage CLIENT role gating', () => {
  it('hides asset-management controls from the linked client', async () => {
    const clientProject = { ...mockProject, id: 'project-1', myRole: 'CLIENT' as const }
    const inProjectAsset = { ...mockAsset, id: 'asset-1', title: 'In Project Beat', projectId: 'project-1', projectName: 'Test Project' }
    server.use(
      http.get(`${BASE}/api/v1/projects/project-1`, () => HttpResponse.json(clientProject)),
      http.get(`${BASE}/api/v1/projects/project-1/assets`, () => HttpResponse.json([inProjectAsset])),
    )

    renderWithProviders(<TestApp />, { route: '/projects/project-1' })

    await screen.findByText(mockProject.name)
    expect(await screen.findByText('In Project Beat')).toBeInTheDocument()
    // View-only, not hidden -- the client can still see what's in the project.
    expect(screen.queryByLabelText(/add an existing asset/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /new asset/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /remove/i })).not.toBeInTheDocument()
  })
})
