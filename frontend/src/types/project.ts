// Mirrors ProjectStatus.java.
export const PROJECT_STATUSES = ['PLANNING', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED'] as const
export type ProjectStatus = (typeof PROJECT_STATUSES)[number]

// Mirrors ProjectRole.java -- the caller's own relationship to a Project, computed by the
// backend. Never derive this on the frontend; always render from this field. See
// docs/collaboration.md in the backend repo.
export const PROJECT_ROLES = ['OWNER', 'VIEW', 'EDIT', 'CLIENT'] as const
export type ProjectRole = (typeof PROJECT_ROLES)[number]

// Mirrors ProjectResponse.java.
export interface Project {
  id: string
  name: string
  description: string | null
  status: ProjectStatus
  createdAt: string
  updatedAt: string
  clientId: string | null
  clientName: string | null
  myRole: ProjectRole
}

// Mirrors CreateProjectRequest.java.
export interface CreateProjectRequest {
  name: string
  description?: string | null
  clientId?: string | null
}

// Mirrors UpdateProjectRequest.java.
export interface UpdateProjectRequest {
  name: string
  description?: string | null
  status: ProjectStatus
  clientId?: string | null
}
