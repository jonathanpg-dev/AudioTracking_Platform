import type { Tag } from './tag'
import type { SortParams } from './sort'

// Mirrors AssetType.java.
export const ASSET_TYPES = ['BEAT', 'COMPOSITION', 'SAMPLE', 'SOUND_EFFECT', 'STEM'] as const
export type AssetType = (typeof ASSET_TYPES)[number]

// Mirrors AssetResponse.java.
export interface Asset {
  id: string
  title: string
  description: string | null
  assetType: AssetType
  bpm: number | null
  musicalKey: string | null
  durationSeconds: number | null
  fileSizeBytes: number | null
  audioFormat: string | null
  createdAt: string
  updatedAt: string
  tags: Tag[]
  projectId: string | null
  projectName: string | null
  hasAudioFile: boolean
  // Feedback from the project's client -- writable only via useUpdateClientNotes by the
  // project's linked client (myRole 'CLIENT'), readable by anyone who can view the asset. Null
  // means no client feedback yet.
  clientNotes: string | null
}

// Mirrors CreateAssetRequest.java.
export interface CreateAssetRequest {
  title: string
  description?: string | null
  assetType: AssetType
  bpm?: number | null
  musicalKey?: string | null
  durationSeconds?: number | null
  fileSizeBytes?: number | null
  audioFormat?: string | null
  projectId?: string | null
}

// Mirrors UpdateAssetRequest.java — full-replace, so every field the form doesn't collect must
// still be resent with its current value (the API layer, not the form, is responsible for that).
export type UpdateAssetRequest = CreateAssetRequest

// Mirrors FileAccessResponse.java.
export interface FileAccessResponse {
  url: string
  expiresAt: string
}

// Mirrors AssetFilter.java's query parameters on GET /assets. tagIds matches AND, not OR -- an
// asset must carry every listed tag, not just one of them (see AssetRepository#search).
export interface AssetFilter extends SortParams {
  assetType?: AssetType
  projectId?: string
  tagIds?: string[]
  minBpm?: number
  maxBpm?: number
  musicalKey?: string
  audioFormat?: string
  minDurationSeconds?: number
  maxDurationSeconds?: number
  page?: number
  size?: number
}
