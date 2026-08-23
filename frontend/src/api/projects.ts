import { api } from './client'
import { toQueryString } from '@/utils/queryString'
import type { CreateProjectRequest, Project, UpdateProjectRequest } from '@/types/project'
import type { SortParams } from '@/types/sort'

export const projectsApi = {
  list: (sort: SortParams = {}) => api.get<Project[]>(`/api/v1/projects${toQueryString(sort)}`),
  // Projects where the caller is the linked CLIENT rather than the owner or a collaborator --
  // deliberately a separate list/endpoint from list() above, not merged into it. See
  // ProjectController#getProjectsAsClient on the backend.
  asClient: (sort: SortParams = {}) => api.get<Project[]>(`/api/v1/projects/as-client${toQueryString(sort)}`),
  get: (id: string) => api.get<Project>(`/api/v1/projects/${id}`),
  create: (body: CreateProjectRequest) => api.post<Project>('/api/v1/projects', body),
  update: (id: string, body: UpdateProjectRequest) => api.put<Project>(`/api/v1/projects/${id}`, body),
  remove: (id: string) => api.delete<void>(`/api/v1/projects/${id}`),
}
