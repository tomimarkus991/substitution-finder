import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
      <TeacherName grid={data} />
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

function TeacherName({ grid }: { grid: Grid }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  // Removing the focused input fires blur (Chrome); after Escape that blur must not save the typed text.
  const cancelled = useRef(false)
  const rename = useMutation({
    mutationFn: (displayName: string) =>
      api<Grid>(`/teachers/${encodeURIComponent(grid.key)}/name`, { method: 'PUT', body: JSON.stringify({ displayName }) }),
    onSuccess: saved => {
      qc.setQueryData(['timetable', 'teacher', grid.key], saved)
      setEditing(false)
    },
    onSettled: () => {
      for (const key of ['timetable', 'candidates', 'day', 'duties']) qc.invalidateQueries({ queryKey: [key] })
    },
  })
  const pdfName = grid.name.includes(' ') ? `${grid.name.slice(grid.name.indexOf(' ') + 1)} ${grid.name.split(' ')[0]}` : grid.name
  const save = (value: string) => { if (value.trim() !== grid.displayName) rename.mutate(value); else setEditing(false) }

  return (
    <div className="mb-4">
      {editing ? (
        <input
          autoFocus
          defaultValue={grid.displayName}
          maxLength={100}
          aria-label="Õpetaja nimi"
          className="field w-full max-w-sm text-lg font-bold"
          onBlur={e => {
            if (cancelled.current) { cancelled.current = false; setEditing(false); return }
            save(e.target.value)
          }}
          onKeyDown={e => {
            if (e.key === 'Enter') e.currentTarget.blur()
            if (e.key === 'Escape') { cancelled.current = true; e.currentTarget.blur() }
          }}
        />
      ) : (
        <h2 className="flex items-center gap-2 text-lg font-bold">
          {grid.displayName}
          <button onClick={() => setEditing(true)} aria-label="Muuda nime" title="Muuda nime"
            className="min-h-11 min-w-11 rounded-xl text-text-caption hover:bg-slate-100">✎</button>
        </h2>
      )}
      {grid.renamed && (
        <p className="text-sm text-text-caption">
          PDF-is: {pdfName} ·{' '}
          <button onClick={() => rename.mutate('')} className="font-semibold text-casual-sky-blue-darker hover:underline">Taasta</button>
        </p>
      )}
      {rename.isError && <p className="text-sm font-semibold text-casual-red-darker">{rename.error.message}</p>}
    </div>
  )
}
