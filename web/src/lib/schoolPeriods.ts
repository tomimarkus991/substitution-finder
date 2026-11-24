/**
 * The school's terms as date ranges up to today, for the Statistika presets.
 * Assumed: the school year starts on 1 September; the first half-year (I poolaasta) runs September to January,
 * the second (II poolaasta) from 1 February. July and August still count as the second half-year.
 */

/** 1 September of the school year `today` falls in. */
export const schoolYearStart = (today: string) => {
  const year = Number(today.slice(0, 4))
  const month = Number(today.slice(5, 7))
  return `${month >= 9 ? year : year - 1}-09-01`
}

/** The first day of the half-year `today` falls in. */
export const halfYearStart = (today: string) => {
  const month = Number(today.slice(5, 7))
  return month >= 9 || month === 1 ? schoolYearStart(today) : `${today.slice(0, 4)}-02-01`
}

type Range = { from: string | null; to: string | null }

/**
 * The preset to highlight for a range. Two presets can cover the same days (in the first half-year the
 * half-year and the school year both start on 1 September), so the pressed one wins; a range picked in the
 * calendar lights the first preset it matches.
 */
export const activePreset = (presets: { label: string; range: Range }[], range: Range, pressed: string | null) => {
  const matches = presets.filter(p => p.range.from === range.from && p.range.to === range.to).map(p => p.label)
  if (pressed && matches.includes(pressed)) return pressed
  return matches[0] ?? null
}
