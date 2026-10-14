import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Absence, TeacherSummary } from '../../api/types'

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
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">Lisa puuduja</h2>

      {teacher ? (
        <div className="mb-3 flex min-h-11 items-center justify-between gap-2 rounded bg-blue-50 px-3">
          <span className="font-medium">{label(teacher)}</span>
          <button onClick={() => setTeacher(null)} className="min-h-11 px-2 text-sm text-blue-700 hover:underline">
            Muuda
          </button>
        </div>
      ) : (
        <div className="mb-3">
          <input
            type="search"
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && matches[0]) { e.preventDefault(); pick(matches[0]) } }}
            placeholder="Otsi õpetajat nime järgi"
            aria-label="Õpetaja"
            className="min-h-11 w-full rounded border border-gray-300 px-3"
          />
          {q && (
            <ul className="mt-1 divide-y divide-gray-100 rounded border border-gray-200">
              {matches.map(t => (
                <li key={t.key}>
                  <button onClick={() => pick(t)} className="min-h-11 w-full px-3 text-left hover:bg-gray-50">
                    {label(t)}
                  </button>
                </li>
              ))}
              {matches.length === 0 && <li className="px-3 py-2 text-sm text-gray-500">Ei leidnud</li>}
            </ul>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-end gap-3">
        <PeriodSelect label="Alates tunnist" value={from} onChange={setFrom} />
        <PeriodSelect label="Kuni tunnini" value={to} onChange={setTo} />
        <button
          onClick={() => add.mutate()}
          disabled={!teacher || !rangeOk || add.isPending}
          className="min-h-11 w-full rounded bg-blue-600 px-4 font-medium text-white disabled:opacity-50 sm:w-auto"
        >
          Lisa puuduja
        </button>
      </div>
      {!rangeOk && <p className="mt-2 text-sm text-red-700">Algustund ei saa olla pärast lõputundi</p>}
    </section>
  )
}

const label = (t: TeacherSummary) => (t.placeholder ? `${t.displayName} (koht)` : t.displayName)

function PeriodSelect({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <label className="flex flex-1 flex-col text-sm text-gray-700 sm:flex-none">
      {label}
      <select
        value={value}
        onChange={e => onChange(Number(e.target.value))}
        className="mt-1 min-h-11 rounded border border-gray-300 bg-white px-3 text-base text-gray-900"
      >
        {PERIODS.map(p => <option key={p} value={p}>{p}.</option>)}
      </select>
    </label>
  )
}
