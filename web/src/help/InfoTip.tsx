import { useEffect, useRef, useState } from 'react'

/** A small (i) next to a confusing term; tapping it shows one or two sentences. */
export default function InfoTip({ label, children }: { label: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (e: Event) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !ref.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('pointerdown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])

  return (
    <span ref={ref} className="relative inline-flex align-middle">
      <button
        type="button"
        onClick={e => { e.preventDefault(); e.stopPropagation(); setOpen(o => !o) }}
        aria-label={`Mis on „${label}“?`}
        aria-expanded={open}
        className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-200 text-xs font-bold text-text-caption hover:bg-casual-sky-blue hover:text-white"
      >
        i
      </button>
      {open && (
        <span
          role="tooltip"
          className="absolute top-8 left-1/2 z-30 w-64 -translate-x-1/2 rounded-2xl bg-text-primary p-3 text-left text-sm font-normal text-white shadow-lg"
        >
          {children}
        </span>
      )}
    </span>
  )
}
