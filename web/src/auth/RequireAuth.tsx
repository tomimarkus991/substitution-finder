import { Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

export default function RequireAuth() {
  const { loggedIn } = useAuth()
  return loggedIn ? <Outlet /> : <Navigate to="/" replace />
}
