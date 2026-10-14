import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { todayIso } from '../lib/dates'

export default function Layout() {
  const { logout } = useAuth()
  return (
    <>
      <nav className="no-print border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center gap-4 px-4 py-3">
          <Link to={`/paev/${todayIso()}`} className="hover:underline">Täna</Link>
          <Link to="/tunniplaan" className="hover:underline">Tunniplaan</Link>
          <Link to="/ajalugu" className="hover:underline">Ajalugu</Link>
          <button onClick={logout} className="ml-auto text-gray-600 hover:underline">Logi välja</button>
        </div>
      </nav>
      <main className="mx-auto max-w-4xl px-4 py-6">
        <Outlet />
      </main>
    </>
  )
}
