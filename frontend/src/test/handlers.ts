import { http, HttpResponse } from 'msw'
import type { CurrentUser } from '@/types/auth'
import type { Asset } from '@/types/asset'
import type { Project } from '@/types/project'
import type { AnalyticsOverview, Activity } from '@/types/analytics'

const BASE = 'http://localhost:8080'

export const mockUser: CurrentUser = {
  id: 'user-1',
  username: 'testuser',
  email: 'testuser@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  isClientOnly: false,
  isLinkedAsClient: false,
}

export const mockAsset: Asset = {
  id: 'asset-1',
  title: 'Test Beat',
  description: null,
  assetType: 'BEAT',
  bpm: 120,
  musicalKey: null,
  durationSeconds: null,
  fileSizeBytes: null,
  audioFormat: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  tags: [],
  projectId: null,
  projectName: null,
  hasAudioFile: false,
  clientNotes: null,
}

export const mockProject: Project = {
  id: 'project-1',
  name: 'Test Project',
  description: null,
  status: 'PLANNING',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  clientId: null,
  clientName: null,
  myRole: 'OWNER',
}

export const mockOverview: AnalyticsOverview = {
  totalAssets: 3,
  totalProjects: 1,
  totalCollections: 0,
  totalClients: 0,
  totalTags: 2,
  totalStorageBytes: 1024,
  totalPlays: 5,
  totalDownloads: 2,
  totalActiveCollaborators: 0,
}

export const mockActivity: Activity = {
  from: '2026-01-01',
  to: '2026-01-07',
  totalEvents: 4,
  changeFromPreviousPeriodPercent: null,
  buckets: [{ date: '2026-01-03', count: 4 }],
}

// A small, reusable default handler set -- individual tests override with server.use(...) for
// scenario-specific responses (errors, empty lists, etc.) rather than duplicating this whole file.
export const handlers = [
  http.get(`${BASE}/api/v1/users/me`, () => HttpResponse.json(mockUser)),
  http.post(`${BASE}/api/v1/users/me/creator-mode`, () => HttpResponse.json({ ...mockUser, isClientOnly: false })),
  http.post(`${BASE}/api/v1/auth/login`, () => HttpResponse.json({ token: 'fake-jwt-token', tokenType: 'Bearer' })),
  http.post(`${BASE}/api/v1/auth/register`, () => HttpResponse.json(mockUser, { status: 201 })),

  http.get(`${BASE}/api/v1/assets`, () => HttpResponse.json([mockAsset])),
  http.get(`${BASE}/api/v1/assets/:id`, () => HttpResponse.json(mockAsset)),
  http.put(`${BASE}/api/v1/assets/:id/client-notes`, () => HttpResponse.json(mockAsset)),

  http.get(`${BASE}/api/v1/projects`, () => HttpResponse.json([mockProject])),
  http.get(`${BASE}/api/v1/projects/as-client`, () => HttpResponse.json([])),
  http.get(`${BASE}/api/v1/projects/:id`, () => HttpResponse.json(mockProject)),
  http.get(`${BASE}/api/v1/projects/:id/assets`, () => HttpResponse.json([mockAsset])),
  http.get(`${BASE}/api/v1/projects/:id/shares`, () => HttpResponse.json([])),

  http.get(`${BASE}/api/v1/collections`, () => HttpResponse.json([])),
  http.get(`${BASE}/api/v1/clients`, () => HttpResponse.json([])),
  http.get(`${BASE}/api/v1/tags`, () => HttpResponse.json([])),

  http.get(`${BASE}/api/v1/analytics/overview`, () => HttpResponse.json(mockOverview)),
  http.get(`${BASE}/api/v1/analytics/assets`, () =>
    HttpResponse.json({ totalUploads: 1, totalPlays: 5, totalDownloads: 2, totalDeletions: 0, topPlayedAssets: [], topDownloadedAssets: [] }),
  ),
  http.get(`${BASE}/api/v1/analytics/activity`, () => HttpResponse.json(mockActivity)),
]
