import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { tagsApi } from '@/api/tags'
import type { CreateTagRequest } from '@/types/tag'

export function useTags() {
  return useQuery({ queryKey: ['tags'], queryFn: tagsApi.list })
}

export function useCreateTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTagRequest) => tagsApi.create(body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['tags'] }),
  })
}
