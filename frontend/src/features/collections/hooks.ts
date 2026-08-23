import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { collectionsApi } from '@/api/collections'
import type { CreateCollectionRequest } from '@/types/collection'
import type { SortParams } from '@/types/sort'

export function useCollections(sort: SortParams = {}) {
  return useQuery({ queryKey: ['collections', sort], queryFn: () => collectionsApi.list(sort) })
}

export function useCollection(id: string | undefined) {
  return useQuery({
    queryKey: ['collections', id],
    queryFn: () => collectionsApi.get(id as string),
    enabled: id !== undefined,
  })
}

export function useCreateCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCollectionRequest) => collectionsApi.create(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['collections'] })
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

export function useUpdateCollection(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCollectionRequest) => collectionsApi.update(id, body),
    onSuccess: (collection) => {
      queryClient.setQueryData(['collections', id], collection)
      void queryClient.invalidateQueries({ queryKey: ['collections'] })
    },
  })
}

export function useDeleteCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => collectionsApi.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: ['collections', id] })
      void queryClient.invalidateQueries({ queryKey: ['collections'] })
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

export function useAddAssetToCollection(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assetId: string) => collectionsApi.addAsset(id, assetId),
    onSuccess: (collection) => queryClient.setQueryData(['collections', id], collection),
  })
}

export function useRemoveAssetFromCollection(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assetId: string) => collectionsApi.removeAsset(id, assetId),
    onSuccess: (collection) => queryClient.setQueryData(['collections', id], collection),
  })
}
