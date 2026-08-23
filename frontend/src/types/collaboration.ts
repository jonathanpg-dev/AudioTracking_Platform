// Mirrors ProjectPermission.java -- the permission a ProjectShare actually grants. Distinct from
// ProjectRole (project.ts), which also covers OWNER.
export const PROJECT_PERMISSIONS = ['VIEW', 'EDIT'] as const
export type ProjectPermission = (typeof PROJECT_PERMISSIONS)[number]

// Mirrors ProjectShareResponse.java.
export interface ProjectShare {
  id: string
  userId: string
  username: string
  email: string
  permission: ProjectPermission
  createdAt: string
}

// Mirrors CreateProjectShareRequest.java.
export interface CreateProjectShareRequest {
  userEmail: string
  permission: ProjectPermission
}

// Mirrors UpdateProjectShareRequest.java.
export interface UpdateProjectShareRequest {
  permission: ProjectPermission
}
