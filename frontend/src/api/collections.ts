import { api } from './client'
import { toQueryString } from '@/utils/queryString'
import type { Collection, CreateCollectionRequest } from '@/types/collection'
import type { SortParams } from '@/types/sort'

export const collectionsApi = {
  list: (sort: SortParams = {}) => api.get<Collection[]>(`/api/v1/collections${toQueryString(sort)}`),
  get: (id: string) => api.get<Collection>(`/api/v1/collections/${id}`),
  create: (body: CreateCollectionRequest) => api.post<Collection>('/api/v1/collections', body),
  update: (id: string, body: CreateCollectionRequest) => api.put<Collection>(`/api/v1/collections/${id}`, body),
  remove: (id: string) => api.delete<void>(`/api/v1/collections/${id}`),
  addAsset: (id: string, assetId: string) => api.post<Collection>(`/api/v1/collections/${id}/assets/${assetId}`),
  removeAsset: (id: string, assetId: string) => api.delete<Collection>(`/api/v1/collections/${id}/assets/${assetId}`),
}
