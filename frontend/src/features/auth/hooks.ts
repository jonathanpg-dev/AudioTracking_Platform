import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuth } from './AuthContext'
import type { GoogleLoginRequest, LoginRequest, RegisterRequest } from '@/types/auth'

// All three flows converge on the same thing: get a token back from the backend, hand it to
// AuthContext#signIn, which is what actually marks the app as authenticated.

export function useLogin() {
  const { signIn } = useAuth()
  return useMutation({
    mutationFn: (body: LoginRequest) => authApi.login(body),
    onSuccess: (data) => signIn(data.token),
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: (body: RegisterRequest) => authApi.register(body),
  })
}

export function useGoogleLogin() {
  const { signIn } = useAuth()
  return useMutation({
    mutationFn: (body: GoogleLoginRequest) => authApi.loginWithGoogle(body),
    onSuccess: (data) => signIn(data.token),
  })
}

// "Become a creator too" -- see NavLinks. Invalidating (not setQueryData) because the query key
// includes the token (['currentUser', token], see AuthContext) which isn't available here; a
// partial-key invalidation matches it regardless. Returning (not `void`-ing) the promise matters:
// invalidateQueries resolves once the refetch actually completes, and NavLinks navigates to
// /dashboard in its own onSuccess -- TanStack Query runs that only after this one settles, so the
// navigation never races ClientOnlyGate reading a still-stale isClientOnly.
export function useUnlockCreatorMode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => authApi.unlockCreatorMode(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['currentUser'] }),
  })
}
