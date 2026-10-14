import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Lesson, TeacherGrid as Grid } from '../../api/types'
import { Card } from '../../components/ui/Card'

const DAYS = ['E', 'T', 'K', 'N', 'R']
const PERIODS = [1, 2, 3, 4, 5, 6, 7]

export default function TeacherGrid({ teacherKey }: { teacherKey: string }) {
  const { data, isError } = useQuery({
    queryKey: ['timetable', 'teacher', teacherKey],
    queryFn: () => api<Grid>(`/timetables/active/teachers/${encodeURIComponent(teacherKey)}`),
  })
  if (isError) return <p className="font-semibold text-casual-red-darker">Tunniplaani ei õnnestunud laadida</p>
  if (!data) return <p className="text-text-caption">Laen…</p>

  const at = (day: number, period: number): Lesson[] =>
    data.lessons.filter(l => l.weekday === day && l.period === period)

  return (
    <Card>
      <h2 className="mb-4 text-lg font-bold">{data.displayName}</h2>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] border-separate border-spacing-1 text-center text-sm">
          <thead>
            <tr>
              <th />
              {PERIODS.map(p => <th key={p} className="pb-1 font-bold text-text-caption">{p}.</th>)}
            </tr>
          </thead>
          <tbody>
            {DAYS.map((d, i) => (
              <tr key={d}>
                <th className="pr-1 font-bold text-text-caption">{d}</th>
                {PERIODS.map(p => {
                  const lessons = at(i + 1, p)
                  return (
                    <td key={p} className={lessons.length ? 'h-14 rounded-xl bg-sky-50 p-1.5 align-top' : 'h-14 rounded-xl bg-slate-50'}>
                      {lessons.map((l, j) => (
                        <div key={j} className="mb-1 last:mb-0">
                          <div className="font-bold text-casual-sky-blue-darker">{l.classes}</div>
                          <div className="text-xs text-text-caption">{[l.subject, l.room].filter(Boolean).join(' ')}</div>
                          {l.note && <div className="text-xs text-text-caption/70">{l.note}</div>}
                        </div>
                      ))}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}
