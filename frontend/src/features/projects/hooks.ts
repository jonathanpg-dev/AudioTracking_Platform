import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'
import type { CreateProjectRequest, UpdateProjectRequest } from '@/types/project'
import type { SortParams } from '@/types/sort'

export function useProjects(sort: SortParams = {}) {
  return useQuery({ queryKey: ['projects', sort], queryFn: () => projectsApi.list(sort) })
}

// Powers ClientProjectsPage -- a deliberately separate query (and cache key) from useProjects
// above, mirroring the backend's separate GET /projects/as-client endpoint.
export function useProjectsAsClient(sort: SortParams = {}) {
  return useQuery({ queryKey: ['projects', 'as-client', sort], queryFn: () => projectsApi.asClient(sort) })
}

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: ['projects', id],
    queryFn: () => projectsApi.get(id as string),
    enabled: id !== undefined,
  })
}

export function useCreateProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProjectRequest) => projectsApi.create(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects'] })
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

export function useUpdateProject(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateProjectRequest) => projectsApi.update(id, body),
    onSuccess: (project) => {
      queryClient.setQueryData(['projects', id], project)
      void queryClient.invalidateQueries({ queryKey: ['projects'] })
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

export function useDeleteProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => projectsApi.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: ['projects', id] })
      void queryClient.invalidateQueries({ queryKey: ['projects'] })
      void queryClient.invalidateQueries({ queryKey: ['assets'] }) // its Assets are now unassigned
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}
