import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import type { TeacherStats } from '../api/types'
import { Card } from '../components/ui/Card'
import { PageTitle } from '../components/ui/PageTitle'

export default function StatsPage() {
  const timetable = useQuery({ queryKey: ['timetable'], queryFn: fetchActiveTimetable })
  const stats = useQuery({ queryKey: ['stats'], queryFn: () => api<TeacherStats[]>('/stats') })

  return (
    <div className="space-y-5">
      <PageTitle hint="Mitu korda keegi on asendanud. Kõige vähem asendanud on eespool.">Statistika</PageTitle>
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
      {timetable.data && stats.data && stats.data.length > 0 && (
        <Card className="p-2">
          <table className="w-full text-left">
            <thead className="text-sm text-text-caption">
              <tr>
                <th className="px-3 py-2 font-semibold">Õpetaja</th>
                <th className="px-3 py-2 text-right font-semibold">Tunnid</th>
                <th className="px-3 py-2 text-right font-semibold">Valved</th>
                <th className="px-3 py-2 text-right font-semibold">Kokku</th>
                <th className="px-3 py-2 text-right font-semibold">Puudutud tunnid</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {stats.data.map(t => (
                <tr key={t.key}>
                  <td className="px-3 py-2 font-semibold">{t.name}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.lessonCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.dutyCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.totalCovers}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.missedLessons}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  )
}
