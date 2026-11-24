import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { todayIso } from '../lib/dates'
import HelpButton from '../help/HelpButton'
import { RealButton } from './ui/RealButton'
import { SlidingNav } from './ui/SlidingNav'
import { ShaderCanvas } from './shader/ShaderCanvas'
import contoursLight from './shader/contours-light.frag?raw'

export default function Layout() {
  const { logout } = useAuth()
  const { pathname } = useLocation()
  const today = `/paev/${todayIso()}`
  return (
    <>
      {/* Faint topographic lines behind every page; cards stay white on top */}
      <ShaderCanvas fragment={contoursLight} className="no-print pointer-events-none fixed inset-0 -z-10 h-full w-full" />

      <nav className="no-print sticky top-0 z-20 border-b border-border/70 bg-base/70 backdrop-blur-md">
        {/* Phones: logo + logout on top, links underneath. Wider screens: one row. */}
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-3 gap-y-2 px-4 py-2.5">
          <Link to={today} className="mr-1 flex items-center gap-2" aria-label="Asendused">
            <img src="/favicon.svg" alt="" className="h-9 w-9" />
            <span className="font-display text-xl font-extrabold tracking-tight text-secondary">Asendused</span>
          </Link>
          <SlidingNav
            className="order-last w-full sm:order-none sm:w-auto"
            items={[
              // "Täna" stays highlighted on any day, not only today
              { to: today, label: 'Täna', active: pathname.startsWith('/paev') },
              { to: '/tunniplaan', label: 'Tunniplaan' },
              { to: '/ajalugu', label: 'Ajalugu' },
              { to: '/statistika', label: 'Statistika' },
              { to: '/opetus', label: 'Õpetus' },
            ]}
          />
          <RealButton variant="light" size="xs" className="ml-auto" onClick={logout}>
            Logi välja
          </RealButton>
        </div>
      </nav>
      {/* Bottom padding keeps the floating help button off the last buttons */}
      <main className="mx-auto max-w-4xl px-4 pt-6 pb-24">
        <Outlet />
      </main>
      <HelpButton />
    </>
  )
}
