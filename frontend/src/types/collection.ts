// Mirrors CollectionResponse.java. Deliberately lists asset ids, not full nested Asset objects --
// fetch individual assets via the assets API when full detail is needed (see useAsset/useAssets).
export interface Collection {
  id: string
  name: string
  createdAt: string
  updatedAt: string
  assetIds: string[]
}

// Mirrors CreateCollectionRequest.java (also reused for update).
export interface CreateCollectionRequest {
  name: string
}
