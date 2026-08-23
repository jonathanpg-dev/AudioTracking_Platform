import { api } from './client'
import type { AuthResponse, CurrentUser, GoogleLoginRequest, LoginRequest, RegisterRequest, User } from '@/types/auth'

export const authApi = {
  register: (body: RegisterRequest) => api.post<User>('/api/v1/auth/register', body),
  login: (body: LoginRequest) => api.post<AuthResponse>('/api/v1/auth/login', body),
  loginWithGoogle: (body: GoogleLoginRequest) => api.post<AuthResponse>('/api/v1/auth/google', body),
  getCurrentUser: () => api.get<CurrentUser>('/api/v1/users/me'),
  // "Become a creator too" -- see CurrentUser.isClientOnly / docs/collaboration.md.
  unlockCreatorMode: () => api.post<CurrentUser>('/api/v1/users/me/creator-mode'),
}
