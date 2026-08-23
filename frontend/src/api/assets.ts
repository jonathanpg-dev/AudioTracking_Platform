import { api } from './client'
import { toQueryString } from '@/utils/queryString'
import type { Asset, AssetFilter, CreateAssetRequest, FileAccessResponse, UpdateAssetRequest } from '@/types/asset'

export const assetsApi = {
  list: (filter: AssetFilter = {}) => api.get<Asset[]>(`/api/v1/assets${toQueryString(filter)}`),
  listForProject: (projectId: string) => api.get<Asset[]>(`/api/v1/projects/${projectId}/assets`),
  get: (id: string) => api.get<Asset>(`/api/v1/assets/${id}`),
  create: (body: CreateAssetRequest) => api.post<Asset>('/api/v1/assets', body),
  update: (id: string, body: UpdateAssetRequest) => api.put<Asset>(`/api/v1/assets/${id}`, body),
  remove: (id: string) => api.delete<void>(`/api/v1/assets/${id}`),

  uploadFile: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.postForm<Asset>(`/api/v1/assets/${id}/file`, formData)
  },
  // `download=true` only changes which analytics event the backend records (ASSET_DOWNLOADED vs
  // ASSET_PLAYED) -- the URL and access check are identical either way. See the audio player and
  // the download button, which are the only two callers of this.
  getFileAccessUrl: (id: string, download: boolean) =>
    api.get<FileAccessResponse>(`/api/v1/assets/${id}/file${toQueryString({ download })}`),
  removeFile: (id: string) => api.delete<Asset>(`/api/v1/assets/${id}/file`),

  addTag: (id: string, tagId: string) => api.post<Asset>(`/api/v1/assets/${id}/tags/${tagId}`),
  removeTag: (id: string, tagId: string) => api.delete<Asset>(`/api/v1/assets/${id}/tags/${tagId}`),

  // Writable only by the project's linked client (myRole 'CLIENT') -- see
  // AssetService#updateClientNotes on the backend for the actual enforcement.
  updateClientNotes: (id: string, clientNotes: string | null) =>
    api.put<Asset>(`/api/v1/assets/${id}/client-notes`, { clientNotes }),
}
