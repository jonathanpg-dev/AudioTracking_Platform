// Mirrors ClientResponse.java.
export interface Client {
  id: string
  name: string
  email: string | null
  company: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

// Mirrors CreateClientRequest.java.
export interface CreateClientRequest {
  name: string
  email?: string | null
  company?: string | null
  notes?: string | null
}

// Mirrors UpdateClientRequest.java.
export type UpdateClientRequest = CreateClientRequest
