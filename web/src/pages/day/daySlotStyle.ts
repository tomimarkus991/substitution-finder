import type { SlotKind } from '../../api/types'

/** From this many periods in a row the count turns orange. */
export const LONG_STREAK = 5

export const SLOT_STYLE: Record<SlotKind, string> = {
  LESSON: 'border-secondary bg-secondary text-white',
  SUBSTITUTING: 'border-primary-dark bg-primary text-white',
  DUTY: 'border-violet-500 bg-white text-violet-700',
  EMPTY: 'border-dashed border-slate-300 bg-white text-slate-400',
}

/** The period being filled: a sky ring around whatever the slot is. */
export const CURRENT_SLOT = 'ring-2 ring-primary ring-offset-2'
