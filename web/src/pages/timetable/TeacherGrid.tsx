import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Lesson, TeacherGrid as Grid } from '../../api/types'

const DAYS = ['E', 'T', 'K', 'N', 'R']
const PERIODS = [1, 2, 3, 4, 5, 6, 7]

export default function TeacherGrid({ teacherKey }: { teacherKey: string }) {
  const { data } = useQuery({
    queryKey: ['timetable', 'teacher', teacherKey],
    queryFn: () => api<Grid>(`/timetables/active/teachers/${encodeURIComponent(teacherKey)}`),
  })
  if (!data) return <p className="text-sm text-gray-600">Laen…</p>

  const at = (day: number, period: number): Lesson[] =>
    data.lessons.filter(l => l.weekday === day && l.period === period)

  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">{data.displayName}</h2>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[480px] border-collapse text-center text-sm">
          <thead>
            <tr>
              <th className="border border-gray-200 p-1" />
              {PERIODS.map(p => <th key={p} className="border border-gray-200 p-1">{p}</th>)}
            </tr>
          </thead>
          <tbody>
            {DAYS.map((d, i) => (
              <tr key={d}>
                <th className="border border-gray-200 p-1">{d}</th>
                {PERIODS.map(p => (
                  <td key={p} className="h-12 border border-gray-200 p-1 align-top">
                    {at(i + 1, p).map((l, j) => (
                      <div key={j} className="mb-1 last:mb-0">
                        <div className="font-medium">{l.classes}</div>
                        <div className="text-xs text-gray-500">{[l.subject, l.room].filter(Boolean).join(' ')}</div>
                      </div>
                    ))}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
