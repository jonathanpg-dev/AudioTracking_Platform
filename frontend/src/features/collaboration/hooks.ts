import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { collaborationApi } from '@/api/collaboration'
import type { CreateProjectShareRequest, UpdateProjectShareRequest } from '@/types/collaboration'

export function useProjectShares(projectId: string) {
  return useQuery({
    queryKey: ['projects', projectId, 'shares'],
    queryFn: () => collaborationApi.list(projectId),
  })
}

export function useCreateShare(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProjectShareRequest) => collaborationApi.create(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'shares'] })
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

export function useUpdateSharePermission(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shareId, body }: { shareId: string; body: UpdateProjectShareRequest }) =>
      collaborationApi.updatePermission(projectId, shareId, body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'shares'] }),
  })
}

export function useRemoveShare(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (shareId: string) => collaborationApi.remove(projectId, shareId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'shares'] }),
  })
}
