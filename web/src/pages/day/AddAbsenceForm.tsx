import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Absence, TeacherSummary } from '../../api/types'
import { Card } from '../../components/ui/Card'
import { RealButton } from '../../components/ui/RealButton'

const PERIODS = [1, 2, 3, 4, 5, 6, 7]
const MAX_MATCHES = 8

/** Lowercase without diacritics, so "parn" finds "Pärnpuu". */
const fold = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

type Props = { date: string; teachers: TeacherSummary[]; absentKeys: Set<string> }

export default function AddAbsenceForm({ date, teachers, absentKeys }: Props) {
  const qc = useQueryClient()
  const [query, setQuery] = useState('')
  const [teacher, setTeacher] = useState<TeacherSummary | null>(null)
  const [from, setFrom] = useState(1)
  const [to, setTo] = useState(7)

  const add = useMutation({
    mutationFn: () =>
      api<Absence>(`/days/${date}/absences`, {
        method: 'POST',
        body: JSON.stringify({ teacherKey: teacher!.key, fromPeriod: from, toPeriod: to }),
      }),
    onSuccess: () => {
      setTeacher(null)
      setQuery('')
      setFrom(1)
      setTo(7)
      qc.invalidateQueries({ queryKey: ['day', date] })
      qc.invalidateQueries({ queryKey: ['candidates', date] })
    },
  })

  const q = fold(query.trim())
  const matches = q
    ? teachers
        .filter(t => !absentKeys.has(t.key) && (fold(t.displayName).includes(q) || fold(t.key).includes(q)))
        .slice(0, MAX_MATCHES)
    : []
  const rangeOk = from <= to

  const pick = (t: TeacherSummary) => { setTeacher(t); setQuery('') }

  return (
    <Card>
      <h2 className="mb-3 text-lg font-bold">Lisa puuduja</h2>

      {teacher ? (
        <div className="mb-4 flex min-h-11 items-center justify-between gap-2 rounded-2xl bg-sky-50 py-1 pr-1 pl-4">
          <span className="font-semibold text-casual-sky-blue-darker">{label(teacher)}</span>
          <RealButton variant="light" size="xs" onClick={() => setTeacher(null)}>Muuda</RealButton>
        </div>
      ) : (
        <div className="relative mb-4">
          <input
            type="search"
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && matches[0]) { e.preventDefault(); pick(matches[0]) } }}
            placeholder="🔍  Otsi õpetajat nime järgi"
            aria-label="Õpetaja"
            className="field w-full"
          />
          {q && (
            <ul className="absolute inset-x-0 top-full z-10 mt-2 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg">
              {matches.map(t => (
                <li key={t.key}>
                  <button onClick={() => pick(t)} className="min-h-11 w-full px-4 text-left font-medium hover:bg-sky-50">
                    {label(t)}
                  </button>
                </li>
              ))}
              {matches.length === 0 && <li className="px-4 py-3 text-text-caption">Ei leidnud</li>}
            </ul>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-end gap-3">
        <PeriodSelect label="Alates tunnist" value={from} onChange={setFrom} />
        <PeriodSelect label="Kuni tunnini" value={to} onChange={setTo} />
        <RealButton
          variant="sky"
          size="sm"
          className="w-full sm:ml-auto sm:w-auto"
          onClick={() => add.mutate()}
          disabled={!teacher || !rangeOk || add.isPending}
        >
          + Lisa puuduja
        </RealButton>
      </div>
      {!rangeOk && <p className="mt-2 text-sm font-medium text-casual-red-darker">Algustund ei saa olla pärast lõputundi</p>}
    </Card>
  )
}

const label = (t: TeacherSummary) => (t.placeholder ? `${t.displayName} (koht)` : t.displayName)

function PeriodSelect({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <label className="flex flex-1 flex-col text-sm font-semibold text-text-caption sm:flex-none">
      {label}
      <select value={value} onChange={e => onChange(Number(e.target.value))} className="field mt-1">
        {PERIODS.map(p => <option key={p} value={p}>{p}. tund</option>)}
      </select>
    </label>
  )
}
