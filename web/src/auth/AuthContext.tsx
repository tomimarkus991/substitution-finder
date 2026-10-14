import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { api, setUnauthorizedHandler, tokenStore } from '../api/client'
import type { AuthResponse } from '../api/types'

type Auth = { loggedIn: boolean; login: (idToken: string) => Promise<void>; logout: () => void }
const AuthContext = createContext<Auth | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [loggedIn, setLoggedIn] = useState(() => tokenStore.get() !== null)
  const navigate = useNavigate()

  useEffect(() => setUnauthorizedHandler(() => { setLoggedIn(false); navigate('/') }), [navigate])

  const login = async (idToken: string) => {
    const res = await api<AuthResponse>('/auth/google', { method: 'POST', body: JSON.stringify({ idToken }) })
    tokenStore.set(res.token)
    setLoggedIn(true)
  }
  const logout = () => { tokenStore.clear(); setLoggedIn(false); navigate('/') }

  return <AuthContext value={{ loggedIn, login, logout }}>{children}</AuthContext>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
