import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getToken, onUnauthorized, setToken } from '../api/client'
import { auth } from '../api/bolso'
import type { Me } from '../api/types'

interface AuthState {
  token: string | null
  me: Me | null
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshMe: () => Promise<Me | null>
  replaceToken: (token: string) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken())
  const [me, setMe] = useState<Me | null>(null)

  const clear = useCallback(() => {
    setToken(null)
    setTokenState(null)
    setMe(null)
  }, [])

  useEffect(() => onUnauthorized(clear), [clear])

  const refreshMe = useCallback(async () => {
    try {
      const m = await auth.me()
      setMe(m)
      return m
    } catch {
      return null
    }
  }, [])

  useEffect(() => {
    if (token) void refreshMe()
  }, [token, refreshMe])

  const start = useCallback((t: string) => {
    setToken(t)
    setTokenState(t)
  }, [])

  const value = useMemo<AuthState>(() => ({
    token, me, refreshMe,
    replaceToken: start,
    login: async (email, password) => start((await auth.login(email.trim(), password)).token),
    register: async (name, email, password) => start((await auth.register(name.trim(), email.trim(), password)).token),
    logout: async () => {
      try { await auth.logout() } catch { /* sessão já encerrada no servidor */ }
      clear()
    },
  }), [token, me, refreshMe, start, clear])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth fora do AuthProvider')
  return ctx
}
