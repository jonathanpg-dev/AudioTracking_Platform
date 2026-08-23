import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clientsApi } from '@/api/clients'
import type { CreateClientRequest, UpdateClientRequest } from '@/types/client'

export function useClients() {
  return useQuery({ queryKey: ['clients'], queryFn: clientsApi.list })
}

export function useClient(id: string | undefined) {
  return useQuery({
    queryKey: ['clients', id],
    queryFn: () => clientsApi.get(id as string),
    enabled: id !== undefined,
  })
}

export function useCreateClient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateClientRequest) => clientsApi.create(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clients'] })
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

export function useUpdateClient(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateClientRequest) => clientsApi.update(id, body),
    onSuccess: (client) => {
      queryClient.setQueryData(['clients', id], client)
      void queryClient.invalidateQueries({ queryKey: ['clients'] })
    },
  })
}

export function useDeleteClient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => clientsApi.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: ['clients', id] })
      void queryClient.invalidateQueries({ queryKey: ['clients'] })
      void queryClient.invalidateQueries({ queryKey: ['projects'] }) // their clientName is now null
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}
