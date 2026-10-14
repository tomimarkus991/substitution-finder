import { useState } from 'react'
import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Candidate, Day, Substitution } from '../../api/types'
import { RealButton } from '../../components/ui/RealButton'
import { cn } from '../../lib/cn'
import { subjectName } from '../../lib/subjects'
import InfoTip from '../../help/InfoTip'

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
    <div className="space-y-5 border-t-2 border-slate-100 bg-surface-bg px-4 py-4">
      {current.substituteKeys.length > 0 && (
        <Group title="Valitud">
          {current.substituteKeys.map(k => (
            <ToggleButton key={k} selected onClick={() => toggle(k)}>
              <span>✓ {names.get(k) ?? k}</span>
            </ToggleButton>
          ))}
        </Group>
      )}

      {candidates.isLoading && <p className="text-sm text-text-caption">Laen vabu õpetajaid…</p>}
      {candidates.data && (
        <>
          <Group
            title="Vaba"
            dot="bg-casual-green"
            tip={<InfoTip label="Vaba">On sel päeval koolis ja tal on just siis auk, või on see tund kohe pärast tema viimast tundi.</InfoTip>}
            empty={free.length === 0 ? 'Vabu õpetajaid pole' : undefined}
          >
            {free.map(c => <CandidateButton key={c.key} candidate={c} onClick={() => toggle(c.key)} />)}
          </Group>
          {askFirst.length > 0 && (
            <Group
              title="Küsi enne"
              hint="peaks tulema tund varem"
              dot="bg-casual-orange"
              tip={<InfoTip label="Küsi enne">See tund on kohe enne tema esimest tundi. Ta peaks tulema tund varem, nii et lepi temaga enne kokku.</InfoTip>}
            >
              {askFirst.map(c => <CandidateButton key={c.key} candidate={c} onClick={() => toggle(c.key)} />)}
            </Group>
          )}
          {others.some(c => c.duties.length > 0) && (
            <p className="flex items-center gap-2 text-sm text-text-caption">
              <span className="h-3 w-3 rounded-full bg-violet-500" /> on sel ajal vahetunni valves
              <InfoTip label="Vahetunni valve">Õpetajal on selle tunni ajal õue- või söögivahetunni valve. Kui ta asendab, peab valve jaoks kellegi teise leidma.</InfoTip>
            </p>
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
        className="field w-full"
      />

      <div className="flex items-center justify-between gap-3">
        <span className="text-sm text-text-caption" aria-live="polite">{saving ? 'Salvestan…' : ''}</span>
        <RealButton variant="dark" size="sm" onClick={onClose}>Valmis</RealButton>
      </div>
    </div>
  )
}

type GroupProps = { title: string; hint?: string; dot?: string; tip?: React.ReactNode; empty?: string; children: React.ReactNode }

function Group({ title, hint, dot, tip, empty, children }: GroupProps) {
  return (
    <div>
      <h3 className="mb-2 flex items-center gap-2 text-sm font-bold">
        {dot && <span className={cn('h-3 w-3 rounded-full', dot)} />}
        {title}
        {tip}
        {hint && <span className="font-normal text-text-caption">· {hint}</span>}
      </h3>
      {empty ? <p className="text-sm text-text-caption">{empty}</p> : <div className="flex flex-wrap gap-2">{children}</div>}
    </div>
  )
}

function CandidateButton({ candidate: c, onClick }: { candidate: Candidate; onClick: () => void }) {
  const tone =
    c.status === 'FREE'
      ? 'border-casual-green-outline border-b-casual-green bg-green-50 hover:bg-green-100'
      : 'border-casual-orange-outline border-b-casual-orange bg-orange-50 hover:bg-orange-100'
  return (
    <button
      onClick={onClick}
      className={cn(
        'min-h-11 w-full rounded-2xl border-2 border-b-4 px-4 py-2 text-left transition-transform hover:-translate-y-0.5 active:translate-y-0.5 sm:w-auto',
        tone,
      )}
    >
      <span className="block font-bold">{c.name}</span>
      {c.duties.map(d => (
        <span key={d} className="mt-1 mr-1 inline-block rounded-full bg-violet-500 px-2 py-0.5 text-xs font-bold text-white">{d}</span>
      ))}
      {(c.subjects.length > 0 || c.todayCount > 0) && (
        <span className="block text-xs text-text-caption">
          {c.subjects.map(subjectName).join(', ')}
          {c.subjects.length > 0 && c.todayCount > 0 && ' · '}
          {c.todayCount > 0 && <span className="font-bold text-text-primary">täna {c.todayCount} {c.todayCount === 1 ? 'asendus' : 'asendust'}</span>}
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
      className={cn(
        'min-h-11 rounded-2xl border-2 border-b-4 px-4 font-semibold transition-transform hover:-translate-y-0.5 active:translate-y-0.5',
        selected
          ? 'border-casual-sky-blue-darker bg-casual-sky-blue text-white'
          : 'border-slate-200 bg-white text-text-primary hover:bg-slate-50',
      )}
    >
      {children}
    </button>
  )
}
