import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '@/api/auth'
import { setUnauthorizedHandler } from '@/api/client'
import { clearToken, getToken, setToken } from '@/api/tokenStorage'
import type { CurrentUser } from '@/types/auth'

interface AuthContextValue {
  user: CurrentUser | null
  isLoading: boolean
  isAuthenticated: boolean
  signIn: (token: string) => void
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // A token stored in state (not just localStorage) so signIn/signOut trigger a re-render and
  // the "who am I" query below reacts to them immediately, instead of only after a page reload.
  const [token, setTokenState] = useState<string | null>(() => getToken())
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const { data: user, isLoading, isError } = useQuery({
    queryKey: ['currentUser', token],
    queryFn: authApi.getCurrentUser,
    enabled: token !== null,
    retry: false,
    staleTime: Infinity,
  })

  const signOut = useCallback(() => {
    clearToken()
    setTokenState(null)
    queryClient.clear()
  }, [queryClient])

  // Wired once into the API client so ANY request across the app that comes back 401 signs the
  // user out and returns them to login -- see api/client.ts.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      signOut()
      navigate('/login', { replace: true })
    })
  }, [signOut, navigate])

  const signIn = useCallback((newToken: string) => {
    setToken(newToken)
    setTokenState(newToken)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: user ?? null,
      // Only "loading" while we have a token and are still confirming who it belongs to --
      // never true once we know for sure either way.
      isLoading: token !== null && isLoading,
      isAuthenticated: token !== null && !isError && user != null,
      signIn,
      signOut,
    }),
    [user, isLoading, isError, token, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
