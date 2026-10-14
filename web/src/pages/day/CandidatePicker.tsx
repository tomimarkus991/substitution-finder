import { useState } from 'react'
import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Candidate, Day, Substitution } from '../../api/types'
import { subjectName } from '../../lib/subjects'

const QUICK_TEXTS = ['Koju', 'Tulevad hiljem', 'Klass kokku', 'Iseseisev töö']

type Choice = { substituteKeys: string[]; freeText: string }
type Props = { date: string; row: Substitution; onClose: () => void }

export default function CandidatePicker({ date, row, onClose }: Props) {
  const qc = useQueryClient()
  const candidates = useQuery({
    queryKey: ['candidates', date, row.period],
    queryFn: () => api<Candidate[]>(`/days/${date}/candidates?period=${row.period}`),
  })

  // Changes are saved one after another (same scope) and shown at once from `draft`;
  // once nothing is in flight, the saved row is the truth again.
  const mutationKey = ['substitution', row.id]
  const saving = useIsMutating({ mutationKey }) > 0
  const [draft, setDraft] = useState<Choice | null>(null)
  const save = useMutation({
    mutationKey,
    scope: { id: `substitution-${row.id}` },
    mutationFn: (c: Choice) => api<Substitution>(`/substitutions/${row.id}`, { method: 'PUT', body: JSON.stringify(c) }),
    onSuccess: saved => {
      qc.setQueryData<Day>(['day', date], day => day && {
        ...day,
        absences: day.absences.map(a => ({ ...a, rows: a.rows.map(r => (r.id === saved.id ? saved : r)) })),
      })
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['day', date] })
      qc.invalidateQueries({ queryKey: ['candidates', date] })
    },
  })

  const current: Choice = saving && draft ? draft : { substituteKeys: row.substituteKeys, freeText: row.freeText }
  const change = (next: Choice) => { setDraft(next); save.mutate(next) }

  // Names of the selected teachers: the row's own names first (they may no longer be candidates), then candidates.
  const names = new Map<string, string>()
  const savedNames = row.substituteNames ? row.substituteNames.split(' / ') : []
  row.substituteKeys.forEach((k, i) => names.set(k, savedNames[i] ?? k))
  candidates.data?.forEach(c => names.set(c.key, c.name))

  const selected = new Set(current.substituteKeys)
  const toggle = (key: string) => change({
    ...current,
    substituteKeys: selected.has(key) ? current.substituteKeys.filter(k => k !== key) : [...current.substituteKeys, key],
  })
  const setText = (text: string) => { if (text.trim() !== current.freeText) change({ ...current, freeText: text.trim() }) }

  const others = (candidates.data ?? []).filter(c => !selected.has(c.key))
  const free = others.filter(c => c.status === 'FREE')
  const askFirst = others.filter(c => c.status === 'ASK_FIRST')

  return (
    <div className="space-y-4 border-t border-gray-100 bg-gray-50 px-3 py-3">
      {current.substituteKeys.length > 0 && (
        <Group title="Valitud">
          {current.substituteKeys.map(k => (
            <ToggleButton key={k} selected onClick={() => toggle(k)}>
              <span className="font-medium">✓ {names.get(k) ?? k}</span>
            </ToggleButton>
          ))}
        </Group>
      )}

      {candidates.isLoading && <p className="text-sm text-gray-600">Laen vabu õpetajaid…</p>}
      {candidates.data && (
        <>
          <Group title="Vaba" dot="bg-green-500" empty={free.length === 0 ? 'Vabu õpetajaid pole' : undefined}>
            {free.map(c => <CandidateButton key={c.key} candidate={c} onClick={() => toggle(c.key)} />)}
          </Group>
          {askFirst.length > 0 && (
            <Group title="Küsi enne" hint="peaks tulema tund varem" dot="bg-amber-500">
              {askFirst.map(c => <CandidateButton key={c.key} candidate={c} onClick={() => toggle(c.key)} />)}
            </Group>
          )}
        </>
      )}

      <Group title="Või märkus">
        {QUICK_TEXTS.map(t => (
          <ToggleButton key={t} selected={current.freeText === t} onClick={() => setText(current.freeText === t ? '' : t)}>
            {t}
          </ToggleButton>
        ))}
      </Group>
      <input
        // Remount when the saved text changes, so a quick button's text shows up here too.
        key={current.freeText}
        defaultValue={current.freeText}
        onBlur={e => setText(e.target.value)}
        onKeyDown={e => { if (e.key === 'Enter') e.currentTarget.blur() }}
        placeholder="Oma tekst (salvestub Enteriga)"
        aria-label="Oma tekst"
        className="min-h-11 w-full rounded border border-gray-300 bg-white px-3"
      />

      <div className="flex items-center justify-between gap-3">
        <span className="text-sm text-gray-500" aria-live="polite">{saving ? 'Salvestan…' : ''}</span>
        <button onClick={onClose} className="min-h-11 rounded bg-gray-800 px-6 font-medium text-white">
          Valmis
        </button>
      </div>
    </div>
  )
}

type GroupProps = { title: string; hint?: string; dot?: string; empty?: string; children: React.ReactNode }

function Group({ title, hint, dot, empty, children }: GroupProps) {
  return (
    <div>
      <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold text-gray-700">
        {dot && <span className={`h-2.5 w-2.5 rounded-full ${dot}`} />}
        {title}
        {hint && <span className="font-normal text-gray-500">· {hint}</span>}
      </h3>
      {empty ? <p className="text-sm text-gray-500">{empty}</p> : <div className="flex flex-wrap gap-2">{children}</div>}
    </div>
  )
}

function CandidateButton({ candidate: c, onClick }: { candidate: Candidate; onClick: () => void }) {
  const tone = c.status === 'FREE' ? 'border-green-300 bg-green-50 hover:bg-green-100' : 'border-amber-300 bg-amber-50 hover:bg-amber-100'
  return (
    <button onClick={onClick} className={`min-h-11 w-full rounded border px-3 py-1.5 text-left sm:w-auto ${tone}`}>
      <span className="block font-medium">{c.name}</span>
      {(c.subjects.length > 0 || c.todayCount > 0) && (
        <span className="block text-xs text-gray-600">
          {c.subjects.map(subjectName).join(', ')}
          {c.subjects.length > 0 && c.todayCount > 0 && ' · '}
          {c.todayCount > 0 && <span className="font-medium text-gray-800">täna {c.todayCount} {c.todayCount === 1 ? 'asendus' : 'asendust'}</span>}
        </span>
      )}
    </button>
  )
}

function ToggleButton({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={selected}
      className={`min-h-11 rounded border px-3 ${
        selected ? 'border-blue-600 bg-blue-600 text-white' : 'border-gray-300 bg-white text-gray-900 hover:bg-gray-100'
      }`}
    >
      {children}
    </button>
  )
}
