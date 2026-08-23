// Mirrors TagResponse.java.
export interface Tag {
  id: string
  name: string
  createdAt: string
}

// Mirrors CreateTagRequest.java (also reused for update — Tag has no other editable field).
export interface CreateTagRequest {
  name: string
}
