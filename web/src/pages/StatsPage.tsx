import { useState } from 'react'
import { Link } from 'react-router'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import type { TeacherStats } from '../api/types'
import { Card } from '../components/ui/Card'
import { type DateRange, type DateRangeLabels, DateRangePicker } from '../components/ui/DateRangePicker'
import { LetterDecryptor } from '../components/ui/LetterDecryptor'
import { PageTitle } from '../components/ui/PageTitle'
import { cn } from '../lib/cn'
import { todayIso } from '../lib/dates'
import { activePreset, halfYearStart, schoolYearStart } from '../lib/schoolPeriods'

// Validated with the dataviz palette script against white: CVD ΔE 9.3, both in the lightness band.
// Lessons sit just under 3:1 contrast, so every bar carries its figure as text and the table stays available.
const LESSON = '#3b9be6'
const LESSON_TRACK = '#d4ebfd'
const DUTY = '#8b5cf6' // the same violet as duties on the day page

const ET_LABELS: DateRangeLabels = {
  name: 'Ajavahemik',
  any: 'Kogu aeg',
  only: d => `Ainult ${d}`,
  between: (a, b) => `${a} – ${b}`,
  since: d => `Alates ${d}`,
  until: d => `Kuni ${d}`,
  previousMonth: 'Eelmine kuu',
  nextMonth: 'Järgmine kuu',
  wholeMonth: m => `Vali kogu ${m}`,
  pickFirst: 'Vali esimene päev või vajuta kuu nimele, et valida terve kuu.',
  pickSecond: 'Nüüd vali viimane päev, või sama päev uuesti, kui tahad ainult seda päeva.',
}

export default function StatsPage() {
  const [range, setRangeState] = useState<DateRange>({ from: null, to: null })
  /** The preset button pressed last; cleared when the calendar sets the range. */
  const [pressed, setPressed] = useState<string | null>(null)
  const setRange = (next: DateRange, preset: string | null = null) => {
    setRangeState(next)
    setPressed(preset)
  }
  const today = todayIso()
  const presets = [
    { label: 'See poolaasta', range: { from: halfYearStart(today), to: today } },
    { label: 'See õppeaasta', range: { from: schoolYearStart(today), to: today } },
  ]
  const active = activePreset(presets, range, pressed)
  const params = new URLSearchParams()
  if (range.from) params.set('from', range.from)
  if (range.to) params.set('to', range.to)
  const timetable = useQuery({ queryKey: ['timetable'], queryFn: fetchActiveTimetable })
  const stats = useQuery({
    queryKey: ['stats', range.from, range.to],
    queryFn: () => api<TeacherStats[]>(`/stats${params.size ? `?${params}` : ''}`),
    placeholderData: keepPreviousData, // keep the charts while another range loads
  })

  return (
    <div className="space-y-5">
      <PageTitle hint="Mitu korda keegi on asendanud. Kõige rohkem asendanud on eespool.">Statistika</PageTitle>
      {timetable.data && (
        <div className="flex flex-wrap items-center gap-2">
          <DateRangePicker
            value={range}
            onChange={r => setRange(r)}
            locale="et-EE"
            labels={ET_LABELS}
            className="w-full max-w-xs"
          />
          {presets.map(p => (
            <Preset key={p.label} active={active === p.label} onClick={() => setRange(p.range, p.label)}>
              {p.label}
            </Preset>
          ))}
          {(range.from || range.to) && (
            <Preset active={false} onClick={() => setRange({ from: null, to: null })}>Kogu aeg</Preset>
          )}
        </div>
      )}
      {(stats.isLoading || timetable.isLoading) && <p className="text-text-caption">Laen…</p>}
      {stats.isError && <p className="font-semibold text-casual-red-darker">Statistika laadimine ebaõnnestus</p>}
      {timetable.data === null && (
        <Card className="flex items-center gap-4 text-lg">
          <span aria-hidden className="text-3xl">📄</span>
          <p>
            Lae esmalt tunniplaan üles.{' '}
            <Link to="/tunniplaan" className="font-semibold text-casual-sky-blue-darker underline">Tunniplaani lehele</Link>
          </p>
        </Card>
      )}
      {timetable.data && stats.data && stats.data.length > 0 && <Stats rows={stats.data} />}
    </div>
  )
}

function Stats({ rows }: { rows: TeacherStats[] }) {
  const lessons = rows.reduce((s, r) => s + r.lessonCovers, 0)
  const duties = rows.reduce((s, r) => s + r.dutyCovers, 0)
  const total = lessons + duties
  const covered = rows.filter(r => r.totalCovers > 0).length
  const max = Math.max(1, ...rows.map(r => r.totalCovers))
  // Most covers first; ties keep the API's name order (sort is stable)
  const ranked = [...rows].sort((a, b) => b.totalCovers - a.totalCovers)

  return (
    <>
      <div className="grid gap-5 md:grid-cols-[1.2fr_1fr_1fr]">
        <Card className="flex flex-col justify-center">
          <p className="font-semibold text-text-caption">Asendusi kokku</p>
          <p className="font-display text-7xl leading-none font-extrabold tracking-tight text-secondary tabular-nums">
            <LetterDecryptor time={700} glyphs="0123456789">{String(total)}</LetterDecryptor>
          </p>
          <p className="mt-3 flex flex-wrap gap-x-4 gap-y-1 font-semibold">
            <Key color={LESSON}>{lessons} tundi</Key>
            <Key color={DUTY}>{duties} valvet</Key>
          </p>
        </Card>

        <Card className="flex items-center gap-4">
          <Ring
            label={`${covered} õpetajat ${rows.length}-st on asendanud`}
            segments={[{ value: covered, color: LESSON }]}
            track={LESSON_TRACK}
            total={rows.length}
            center={`${covered}/${rows.length}`}
          />
          <div>
            <p className="font-bold">Õpetajad, kes on asendanud</p>
            <p className="text-sm text-text-caption">{covered} õpetajat {rows.length}-st on vähemalt korra asendanud</p>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <Ring
            label={`${lessons} tunniasendust ja ${duties} valveasendust`}
            segments={[{ value: lessons, color: LESSON }, { value: duties, color: DUTY }]}
            track={LESSON_TRACK}
            total={total}
            center={total ? `${Math.round((lessons / total) * 100)}%` : '–'}
          />
          <div>
            <p className="font-bold">Tunnid ja valved</p>
            <p className="text-sm text-text-caption">
              {total ? `${Math.round((lessons / total) * 100)}% asendustest on tunnid, ${100 - Math.round((lessons / total) * 100)}% valved` : 'Asendusi pole veel'}
            </p>
          </div>
        </Card>
      </div>

      <Card>
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-xl font-bold">Õpetajate kaupa</h2>
          <p className="flex gap-4 text-sm font-semibold text-text-caption">
            <Key color={LESSON}>tunnid</Key>
            <Key color={DUTY}>valved</Key>
          </p>
        </div>
        <ul className="mt-4 space-y-1">
          {ranked.map(r => (
            <li key={r.key} className="flex items-center gap-3 rounded-lg px-1 py-1 hover:bg-base">
              <span className="w-32 shrink-0 truncate font-semibold sm:w-44" title={r.name}>{r.name}</span>
              {/* Lessons, then duties, each bar followed by its own count; 6.5rem stays free for the outlined counts */}
              <span className="flex min-w-0 flex-1 items-center gap-1.5 text-sm font-bold tabular-nums">
                {r.totalCovers === 0 && <span className="text-text-caption">0</span>}
                {r.lessonCovers > 0 && <Bar value={r.lessonCovers} max={max} color={LESSON} />}
                {r.dutyCovers > 0 && <Bar value={r.dutyCovers} max={max} color={DUTY} />}
              </span>
              <span className="sr-only">: {r.lessonCovers} tundi, {r.dutyCovers} valvet</span>
            </li>
          ))}
        </ul>
      </Card>

      <details className="group rounded-3xl bg-white shadow-sm">
        <summary className="flex cursor-pointer list-none items-center justify-between rounded-3xl px-5 py-4 font-bold select-none hover:bg-slate-50">
          Näita tabelina
          <span aria-hidden className="text-text-caption transition-transform group-open:rotate-180">▾</span>
        </summary>
        <div className="px-2 pb-2">
          <table className="w-full text-left">
            <thead className="text-sm text-text-caption">
              <tr>
                <th className="px-3 py-2 font-semibold">Õpetaja</th>
                <th className="px-3 py-2 text-right font-semibold">Tunnid</th>
                <th className="px-3 py-2 text-right font-semibold">Valved</th>
                <th className="px-3 py-2 text-right font-semibold">Kokku</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ranked.map(t => (
                <tr key={t.key}>
                  <td className="px-3 py-2 font-semibold">{t.name}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.lessonCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.dutyCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.totalCovers}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </>
  )
}

/** A quick range next to the calendar; sky when it is the chosen range. */
function Preset({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'rounded-xl border-2 border-b-4 px-3 py-1.5 text-sm font-semibold transition-transform hover:-translate-y-0.5 active:translate-y-0.5',
        active
          ? 'border-casual-sky-blue-darker bg-casual-sky-blue text-white'
          : 'border-slate-200 bg-white text-text-primary hover:bg-slate-50',
      )}
    >
      {children}
    </button>
  )
}

function Bar({ value, max, color }: { value: number; max: number; color: string }) {
  return (
    <>
      <span
        aria-hidden
        className="h-4 shrink-0 rounded-r-[4px]"
        style={{ width: `calc((100% - 6.5rem) * ${value / max})`, background: color }}
      />
      {/* Outlined in its bar's colour, so it is clear which count is lessons and which duties */}
      <span aria-hidden className="mr-1.5 rounded-md border-2 bg-white px-1.5 leading-5" style={{ borderColor: color }}>
        {value}
      </span>
    </>
  )
}

function Key({ color, children }: { color: string; children: React.ReactNode }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-3 w-3 rounded-sm" style={{ background: color }} />
      {children}
    </span>
  )
}

/** A ring: coloured arcs over a light track, 2px surface gaps between arcs, a figure in the middle. */
function Ring({ segments, total, track, center, label }: {
  segments: { value: number; color: string }[]
  total: number
  track: string
  center: string
  label: string
}) {
  const r = 38
  const c = 2 * Math.PI * r
  const shown = segments.filter(s => s.value > 0)
  const gap = shown.length > 1 ? 2 : 0
  let offset = 0
  return (
    <svg viewBox="0 0 100 100" className="h-24 w-24 shrink-0 -rotate-90" role="img" aria-label={label}>
      <circle cx="50" cy="50" r={r} fill="none" stroke={track} strokeWidth="12" />
      {total > 0 && shown.map((s, i) => {
        const len = (s.value / total) * c
        const arc = (
          <circle
            key={i}
            cx="50" cy="50" r={r} fill="none" stroke={s.color} strokeWidth="12"
            strokeDasharray={`${Math.max(0, len - gap)} ${c}`}
            strokeDashoffset={-offset}
          />
        )
        offset += len
        return arc
      })}
      <text
        x="50" y="50" transform="rotate(90 50 50)" textAnchor="middle" dominantBaseline="central"
        className="fill-text font-display text-[19px] font-extrabold"
      >
        {center}
      </text>
    </svg>
  )
}
