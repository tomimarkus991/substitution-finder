import type { Substitution } from '../api/types'

/** Rows with a substitute or free text that a new from–to range would remove. */
export const lostCovers = (rows: Substitution[], from: number, to: number) =>
  rows.filter(r => (r.period < from || r.period > to) && (r.substituteNames || r.freeText))
