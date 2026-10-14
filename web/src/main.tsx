import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GoogleOAuthProvider } from '@react-oauth/google'
import './index.css'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import { ToastProvider, useToast } from './components/Toast'
import LandingPage from './pages/LandingPage'
import DayPage from './pages/DayPage'
import TimetablePage from './pages/TimetablePage'
import HistoryPage from './pages/HistoryPage'

function QueryProvider({ children }: { children: React.ReactNode }) {
  const { showError } = useToast()
  const [client] = useState(() => new QueryClient({
    mutationCache: new MutationCache({ onError: e => showError(e.message) }),
    queryCache: new QueryCache({ onError: e => showError(e.message) }),
  }))
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID} locale="et">
        <ToastProvider>
          <QueryProvider>
            <AuthProvider>
              <Routes>
                <Route path="/" element={<LandingPage />} />
                <Route element={<RequireAuth />}>
                  <Route element={<Layout />}>
                    <Route path="/paev/:date" element={<DayPage />} />
                    <Route path="/tunniplaan" element={<TimetablePage />} />
                    <Route path="/ajalugu" element={<HistoryPage />} />
                  </Route>
                </Route>
                <Route path="*" element={<Navigate to="/" />} />
              </Routes>
            </AuthProvider>
          </QueryProvider>
        </ToastProvider>
      </GoogleOAuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
