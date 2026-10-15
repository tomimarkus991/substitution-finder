import type { DaySlot } from '../../api/types'
import { cn } from '../../lib/cn'
import { LONG_STREAK, SLOT_STYLE } from './daySlotStyle'

type Props = { day: DaySlot[]; period: number; streak: number }

/** The candidate's 7 periods; ★ marks the one being filled. Hovering a square shows its class and subject. */
export default function DayStrip({ day, period, streak }: Props) {
  return (
    <span className="mt-1.5 flex items-end gap-2">
      <span className="sr-only">{streak} tundi järjest</span>
      <span aria-hidden="true" className="flex gap-0.5">
        {day.map(s => (
          <span key={s.period} className="flex flex-col items-center">
            <span
              title={`${s.period}. ${s.label || 'vaba'}`}
              className={cn(
                'flex h-4 w-4 items-center justify-center rounded border-2 text-[9px] leading-none',
                SLOT_STYLE[s.kind],
                s.period === period && 'border-slate-900 ring-2 ring-slate-900',
              )}
            >
              {s.period === period && '★'}
            </span>
            <span className="text-[10px] leading-tight text-text-caption">{s.period}</span>
          </span>
        ))}
      </span>
      <span aria-hidden="true" className={cn('pb-3 text-xs', streak >= LONG_STREAK ? 'font-bold text-casual-orange-darker' : 'text-text-caption')}>
        → {streak} järjest
      </span>
    </span>
  )
}
