import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { RealButton } from '../components/ui/RealButton'
import { AbsenceHelp, DutyHelp, GoodToKnow, SubstituteHelp, TableHelp, TimetableHelp } from './guideSections'

/** The guide sections that matter on the page the teacher is looking at. */
function helpFor(pathname: string) {
  if (pathname.startsWith('/paev')) return <><AbsenceHelp /><SubstituteHelp /><TableHelp /></>
  if (pathname.startsWith('/tunniplaan')) return <><TimetableHelp /><DutyHelp /></>
  return <GoodToKnow />
}

export default function HelpButton() {
  const { pathname } = useLocation()
  // Remembers the page it was opened on, so it closes by itself when the page changes
  const [openOn, setOpenOn] = useState<string | null>(null)
  const open = openOn === pathname
  const setOpen = (v: boolean) => setOpenOn(v ? pathname : null)

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpenOn(null) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  // The full guide is already on screen there
  if (pathname.startsWith('/opetus')) return null

  return (
    <div className="no-print">
      <RealButton
        variant="sky"
        size="icon"
        className="fixed right-4 bottom-4 z-30 h-14 w-14 rounded-full text-2xl shadow-sky"
        onClick={() => setOpen(true)}
        aria-label="Abi"
        aria-expanded={open}
      >
        ?
      </RealButton>

      {open && (
        <div className="fixed inset-0 z-40 flex items-end justify-end sm:items-stretch" role="dialog" aria-modal aria-label="Abi">
          <button aria-label="Sulge abi" className="absolute inset-0 cursor-default bg-black/30" onClick={() => setOpen(false)} />
          {/* Bottom sheet on phones, side panel on wider screens */}
          <div className="relative max-h-[85vh] w-full overflow-y-auto rounded-t-3xl bg-surface-bg p-4 shadow-2xl sm:max-h-none sm:max-w-md sm:rounded-none sm:rounded-l-3xl">
            <div className="sticky -top-4 z-10 -mx-4 -mt-4 mb-4 flex items-center justify-between bg-surface-bg px-4 pt-4 pb-2">
              <h2 className="text-2xl font-extrabold">Abi</h2>
              <RealButton variant="light" size="xs" onClick={() => setOpen(false)}>Sulge</RealButton>
            </div>
            <div className="@container space-y-4">{helpFor(pathname)}</div>
            <Link to="/opetus" className="mt-4 block py-3 text-center font-semibold text-casual-sky-blue-darker hover:underline">
              Kogu õpetus →
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
