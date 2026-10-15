import type { SlotKind } from '../../api/types'

/** From this many periods in a row the count turns orange. */
export const LONG_STREAK = 5

export const SLOT_STYLE: Record<SlotKind, string> = {
  LESSON: 'border-slate-400 bg-slate-400 text-white',
  SUBSTITUTING: 'border-casual-sky-blue bg-casual-sky-blue text-white',
  DUTY: 'border-violet-500 bg-white text-slate-900',
  EMPTY: 'border-slate-200 bg-white text-slate-900',
}
