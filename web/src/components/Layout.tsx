import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { todayIso } from '../lib/dates'
import { RealButton } from './ui/RealButton'

const navLink = ({ isActive }: { isActive: boolean }) =>
  cn(
    'rounded-xl px-3 py-2 font-semibold transition-colors',
    isActive ? 'bg-casual-sky-blue text-white' : 'text-text-caption hover:bg-slate-100 hover:text-text-primary',
  )

export default function Layout() {
  const { logout } = useAuth()
  const { pathname } = useLocation()
  return (
    <>
      <nav className="no-print sticky top-0 z-20 border-b border-slate-200 bg-white/90 backdrop-blur">
        {/* Phones: logo + logout on top, links underneath. Wider screens: one row. */}
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-2 gap-y-1 px-4 py-2">
          <Link to={`/paev/${todayIso()}`} className="mr-2 flex items-center gap-2" aria-label="Asendused">
            <img src="/favicon.svg" alt="" className="h-8 w-8" />
            <span className="text-lg font-bold">Asendused</span>
          </Link>
          <div className="order-last flex w-full justify-between sm:order-none sm:w-auto sm:justify-start sm:gap-1">
            {/* "Täna" stays highlighted on any day, not only today */}
            <NavLink to={`/paev/${todayIso()}`} className={() => navLink({ isActive: pathname.startsWith('/paev') })}>
              Täna
            </NavLink>
            <NavLink to="/tunniplaan" className={navLink}>Tunniplaan</NavLink>
            <NavLink to="/ajalugu" className={navLink}>Ajalugu</NavLink>
            <NavLink to="/opetus" className={navLink}>Õpetus</NavLink>
          </div>
          <RealButton variant="light" size="xs" className="ml-auto" onClick={logout}>
            Logi välja
          </RealButton>
        </div>
      </nav>
      <main className="mx-auto max-w-4xl px-4 py-6">
        <Outlet />
      </main>
    </>
  )
}
