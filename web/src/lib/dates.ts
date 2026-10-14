const NAMES = ['Pühapäev', 'Esmaspäev', 'Teisipäev', 'Kolmapäev', 'Neljapäev', 'Reede', 'Laupäev']
const parse = (iso: string) => { const [y, m, d] = iso.split('-').map(Number); return new Date(y, m - 1, d) }

export const todayIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }
export const shortDate = (iso: string) => { const d = parse(iso); return `${d.getDate()}.${String(d.getMonth() + 1).padStart(2, '0')}` }
export const weekdayName = (iso: string) => NAMES[parse(iso).getDay()]
export const isWeekend = (iso: string) => [0, 6].includes(parse(iso).getDay())
