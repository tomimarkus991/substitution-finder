import { GoogleLogin } from '@react-oauth/google'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { todayIso } from '../lib/dates'

export default function LandingPage() {
  const { loggedIn, login } = useAuth()
  const { showError } = useToast()
  const navigate = useNavigate()
  if (loggedIn) return <Navigate to={`/paev/${todayIso()}`} replace />

  return (
    <main className="mx-auto max-w-xl px-4 py-16">
      <h1 className="text-3xl font-semibold">Asendused</h1>
      <p className="mt-4 text-gray-700">
        Märgi puuduvad õpetajad ja vaata kohe, kes saab nende tunde asendada. Tunniplaan tuleb otse kooli PDF-ist
        ning päeva asenduste tabeli saad kopeerida Wordi või välja printida.
      </p>
      <ul className="mt-6 list-disc pl-5 text-gray-700">
        <li>Näeb, kes on igas tunnis vaba</li>
        <li>Arvestab, millal õpetaja koolis on</li>
        <li>Teeb asenduste tabeli valmis</li>
      </ul>
      <div className="mt-8">
        <GoogleLogin
          onSuccess={async ({ credential }) => {
            if (!credential) return
            try { await login(credential); navigate(`/paev/${todayIso()}`) }
            catch (e) { showError((e as Error).message) }
          }}
          onError={() => showError('Google sisselogimine ebaõnnestus')}
          text="signin_with"
        />
      </div>
      {import.meta.env.DEV && (
        <button
          className="mt-4 rounded border border-gray-400 px-3 py-1 text-sm text-gray-700"
          onClick={() => login('dev').then(() => navigate(`/paev/${todayIso()}`)).catch(e => showError((e as Error).message))}
        >
          Dev login
        </button>
      )}
    </main>
  )
}
