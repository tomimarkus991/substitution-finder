import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import type { TeacherStats } from '../api/types'
import { Card } from '../components/ui/Card'
import { PageTitle } from '../components/ui/PageTitle'
import { RealButton } from '../components/ui/RealButton'
import { monthStart, schoolYearStart, todayIso } from '../lib/dates'

/** Inclusive absence-date range; an empty string is an open end. */
type Range = { from: string; to: string }

const quickRanges = (today: string): { label: string; range: Range }[] => [
  { label: 'See kuu', range: { from: monthStart(today), to: '' } },
  { label: 'See õppeaasta', range: { from: schoolYearStart(today), to: '' } },
  { label: 'Kõik', range: { from: '', to: '' } },
]

const statsPath = ({ from, to }: Range) => {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return query ? `/stats?${query}` : '/stats'
}

export default function StatsPage() {
  const quick = quickRanges(todayIso())
  const [range, setRange] = useState<Range>(quick[1].range)
  const timetable = useQuery({ queryKey: ['timetable'], queryFn: fetchActiveTimetable })
  const stats = useQuery({
    queryKey: ['stats', range.from, range.to],
    queryFn: () => api<TeacherStats[]>(statsPath(range)),
  })

  return (
    <div className="space-y-5">
      <PageTitle hint="Mitu korda keegi on asendanud. Kõige vähem asendanud on eespool.">Statistika</PageTitle>
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="date"
          value={range.from}
          onChange={e => setRange({ ...range, from: e.target.value })}
          aria-label="Alguskuupäev"
          className="field"
        />
        <span aria-hidden className="text-text-caption">–</span>
        <input
          type="date"
          value={range.to}
          onChange={e => setRange({ ...range, to: e.target.value })}
          aria-label="Lõppkuupäev"
          className="field"
        />
        {quick.map(q => {
          const active = q.range.from === range.from && q.range.to === range.to
          return (
            <RealButton key={q.label} variant={active ? 'sky' : 'light'} size="sm" aria-pressed={active}
              onClick={() => setRange(q.range)}>
              {q.label}
            </RealButton>
          )
        })}
      </div>
      {(stats.isLoading || timetable.isLoading) && <p className="text-text-caption">Laen…</p>}
      {stats.isError && <p className="font-semibold text-casual-red-darker">{stats.error.message}</p>}
      {timetable.data === null && (
        <Card className="flex items-center gap-4 text-lg">
          <span aria-hidden className="text-3xl">📄</span>
          <p>
            Lae esmalt tunniplaan üles.{' '}
            <Link to="/tunniplaan" className="font-semibold text-casual-sky-blue-darker underline">Tunniplaani lehele</Link>
          </p>
        </Card>
      )}
      {timetable.data && stats.data && stats.data.length > 0 && (
        <Card className="p-2">
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
              {stats.data.map(t => (
                <tr key={t.key}>
                  <td className="px-3 py-2 font-semibold">{t.name}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.lessonCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.dutyCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.totalCovers}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  )
}
