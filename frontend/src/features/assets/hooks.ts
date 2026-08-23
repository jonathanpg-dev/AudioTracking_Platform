import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { assetsApi } from '@/api/assets'
import type { Asset, AssetFilter, CreateAssetRequest, UpdateAssetRequest } from '@/types/asset'

export function useAssets(filter: AssetFilter = {}) {
  return useQuery({
    queryKey: ['assets', filter],
    queryFn: () => assetsApi.list(filter),
  })
}

export function useProjectAssets(projectId: string | undefined) {
  return useQuery({
    queryKey: ['projects', projectId, 'assets'],
    queryFn: () => assetsApi.listForProject(projectId as string),
    enabled: projectId !== undefined,
  })
}

export function useAsset(id: string | undefined) {
  return useQuery({
    queryKey: ['assets', id],
    queryFn: () => assetsApi.get(id as string),
    enabled: id !== undefined,
  })
}

// A change to one Asset can affect: the general asset list, the analytics numbers, and (if the
// asset belongs to a Project) that Project's own asset list -- see the spec's "Create Asset ->
// invalidate asset list -> invalidate relevant Project query" example.
function invalidateAssetRelated(queryClient: QueryClient, asset?: Pick<Asset, 'projectId'>) {
  void queryClient.invalidateQueries({ queryKey: ['assets'] })
  void queryClient.invalidateQueries({ queryKey: ['analytics'] })
  if (asset?.projectId) {
    void queryClient.invalidateQueries({ queryKey: ['projects', asset.projectId] })
  }
}

export function useCreateAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateAssetRequest) => assetsApi.create(body),
    onSuccess: (asset) => invalidateAssetRelated(queryClient, asset),
  })
}

export function useUpdateAsset(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateAssetRequest) => assetsApi.update(id, body),
    onSuccess: (asset) => {
      queryClient.setQueryData(['assets', id], asset)
      invalidateAssetRelated(queryClient, asset)
    },
  })
}

// Assigns (or unassigns, with projectId: null) an existing asset to a project -- for the "add
// existing asset" flow on ProjectDetailPage, where the target asset is picked dynamically from a
// list rather than fixed for the hook's lifetime (contrast with useUpdateAsset(id), which is for
// AssetFormDialog editing one already-known asset).
//
// There's no dedicated "assign to project" endpoint on the backend -- PUT /assets/{id} is
// full-replace (see UpdateAssetRequest), so every other field has to be resent unchanged. The
// asset is already fully loaded wherever this gets called from (the project's own asset list, or
// the global asset list), so that's just read off the object already in hand.
export function useAssignAssetToProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ asset, projectId }: { asset: Asset; projectId: string | null }) =>
      assetsApi.update(asset.id, {
        title: asset.title,
        description: asset.description,
        assetType: asset.assetType,
        bpm: asset.bpm,
        musicalKey: asset.musicalKey,
        durationSeconds: asset.durationSeconds,
        fileSizeBytes: asset.fileSizeBytes,
        audioFormat: asset.audioFormat,
        projectId,
      }),
    onSuccess: (updatedAsset, variables) => {
      queryClient.setQueryData(['assets', updatedAsset.id], updatedAsset)
      invalidateAssetRelated(queryClient, updatedAsset)
      // invalidateAssetRelated only knows the asset's NEW project -- if it just moved out of (or
      // away from) a different project, that project's own asset list is now stale too and won't
      // otherwise refresh until something else happens to invalidate it.
      const previousProjectId = variables.asset.projectId
      if (previousProjectId && previousProjectId !== updatedAsset.projectId) {
        void queryClient.invalidateQueries({ queryKey: ['projects', previousProjectId] })
      }
    },
  })
}

export function useDeleteAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => assetsApi.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: ['assets', id] })
      invalidateAssetRelated(queryClient)
    },
  })
}

export function useUploadAssetFile(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => assetsApi.uploadFile(id, file),
    onSuccess: (asset) => {
      queryClient.setQueryData(['assets', id], asset)
      invalidateAssetRelated(queryClient, asset)
    },
  })
}

export function useRemoveAssetFile(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => assetsApi.removeFile(id),
    onSuccess: (asset) => {
      queryClient.setQueryData(['assets', id], asset)
      void queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
}

// A fresh presigned URL is fetched on demand every time (play, or download) -- never cached,
// since it's short-lived and re-fetching is exactly what "temporary secure URL" means. This is
// a mutation, not a query, because it's an imperative action (and each call also causes the
// backend to record an ASSET_PLAYED/ASSET_DOWNLOADED analytics event), not idempotent GET state.
export function useAssetFileAccess(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (download: boolean) => assetsApi.getFileAccessUrl(id, download),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['analytics'] }),
  })
}

export function useAddTagToAsset(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tagId: string) => assetsApi.addTag(id, tagId),
    onSuccess: (asset) => queryClient.setQueryData(['assets', id], asset),
  })
}

export function useRemoveTagFromAsset(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tagId: string) => assetsApi.removeTag(id, tagId),
    onSuccess: (asset) => queryClient.setQueryData(['assets', id], asset),
  })
}

// The one write a CLIENT-role viewer is allowed to make -- see AssetDetailPage's Client Notes
// card, gated on canWriteClientNotes.
export function useUpdateClientNotes(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (clientNotes: string | null) => assetsApi.updateClientNotes(id, clientNotes),
    onSuccess: (asset) => queryClient.setQueryData(['assets', id], asset),
  })
}
