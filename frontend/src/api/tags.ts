import { api } from './client'
import type { CreateTagRequest, Tag } from '@/types/tag'

export const tagsApi = {
  list: () => api.get<Tag[]>('/api/v1/tags'),
  create: (body: CreateTagRequest) => api.post<Tag>('/api/v1/tags', body),
  update: (id: string, body: CreateTagRequest) => api.put<Tag>(`/api/v1/tags/${id}`, body),
  remove: (id: string) => api.delete<void>(`/api/v1/tags/${id}`),
}
