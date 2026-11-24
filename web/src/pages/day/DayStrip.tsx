import type { DaySlot } from '../../api/types'
import { cn } from '../../lib/cn'
import { CURRENT_SLOT, LONG_STREAK, SLOT_STYLE } from './daySlotStyle'

type Props = { day: DaySlot[]; period: number; streak: number }

/** The candidate's 7 periods, numbered; ★ in a sky ring marks the one being filled. Hovering a square shows its class and subject. */
export default function DayStrip({ day, period, streak }: Props) {
  return (
    <span className="mt-2 flex items-center gap-3">
      <span className="sr-only">{streak} tundi järjest</span>
      <span aria-hidden="true" className="flex gap-1">
        {day.map(s => (
          <span
            key={s.period}
            title={`${s.period}. ${s.label || 'vaba'}`}
            className={cn(
              'flex h-5 w-5 items-center justify-center rounded-md border-2 text-[11px] leading-none font-bold tabular-nums',
              SLOT_STYLE[s.kind],
              s.period === period && CURRENT_SLOT,
            )}
          >
            {s.period === period ? '★' : s.period}
          </span>
        ))}
      </span>
      <span
        aria-hidden="true"
        className={cn(
          'rounded-full px-2 py-0.5 text-xs font-bold whitespace-nowrap',
          streak >= LONG_STREAK ? 'bg-orange-100 text-casual-orange-darker' : 'bg-slate-100 text-text-caption',
        )}
      >
        → {streak} järjest
      </span>
    </span>
  )
}
