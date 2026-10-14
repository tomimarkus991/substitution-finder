const NAMES = ['Pühapäev', 'Esmaspäev', 'Teisipäev', 'Kolmapäev', 'Neljapäev', 'Reede', 'Laupäev']
const parse = (iso: string) => { const [y, m, d] = iso.split('-').map(Number); return new Date(y, m - 1, d) }

export const todayIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }
export const shortDate = (iso: string) => { const d = parse(iso); return `${d.getDate()}.${String(d.getMonth() + 1).padStart(2, '0')}` }
export const weekdayName = (iso: string) => NAMES[parse(iso).getDay()]
export const isWeekend = (iso: string) => [0, 6].includes(parse(iso).getDay())
const toIso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** A real calendar date in YYYY-MM-DD form. */
export const isIsoDate = (s: string | undefined): s is string =>
  !!s && /^\d{4}-\d{2}-\d{2}$/.test(s) && toIso(parse(s)) === s

/** The previous (-1) or next (1) Monday–Friday date. */
export const shiftSchoolDay = (iso: string, dir: 1 | -1) => {
  const d = parse(iso)
  do d.setDate(d.getDate() + dir)
  while (d.getDay() === 0 || d.getDay() === 6)
  return toIso(d)
}

/** "Reede 4.09.2026" */
export const longDate = (iso: string) => `${weekdayName(iso)} ${shortDate(iso)}.${iso.slice(0, 4)}`
