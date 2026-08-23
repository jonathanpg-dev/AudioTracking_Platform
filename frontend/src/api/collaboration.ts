import { api } from './client'
import type { CreateProjectShareRequest, ProjectShare, UpdateProjectShareRequest } from '@/types/collaboration'

// Every endpoint here is owner-only on the backend (see ProjectShareServiceImpl) -- a
// collaborator calling any of these gets a 403 regardless of what the UI shows them.
export const collaborationApi = {
  list: (projectId: string) => api.get<ProjectShare[]>(`/api/v1/projects/${projectId}/shares`),
  create: (projectId: string, body: CreateProjectShareRequest) =>
    api.post<ProjectShare>(`/api/v1/projects/${projectId}/shares`, body),
  updatePermission: (projectId: string, shareId: string, body: UpdateProjectShareRequest) =>
    api.put<ProjectShare>(`/api/v1/projects/${projectId}/shares/${shareId}`, body),
  remove: (projectId: string, shareId: string) => api.delete<void>(`/api/v1/projects/${projectId}/shares/${shareId}`),
}
