import { api } from './client'
import type { Client, CreateClientRequest, UpdateClientRequest } from '@/types/client'

export const clientsApi = {
  list: () => api.get<Client[]>('/api/v1/clients'),
  get: (id: string) => api.get<Client>(`/api/v1/clients/${id}`),
  create: (body: CreateClientRequest) => api.post<Client>('/api/v1/clients', body),
  update: (id: string, body: UpdateClientRequest) => api.put<Client>(`/api/v1/clients/${id}`, body),
  remove: (id: string) => api.delete<void>(`/api/v1/clients/${id}`),
}
