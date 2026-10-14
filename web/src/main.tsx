import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { GoogleOAuthProvider } from '@react-oauth/google'
import './index.css'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import QueryProvider from './components/QueryProvider'
import { ToastProvider } from './components/Toast'
import LandingPage from './pages/LandingPage'
import DayPage from './pages/DayPage'
import TimetablePage from './pages/TimetablePage'
import HistoryPage from './pages/HistoryPage'
import GuidePage from './pages/GuidePage'

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
                    <Route path="/opetus" element={<GuidePage />} />
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
