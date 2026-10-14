import { useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { fetchActiveTimetable, fetchDuties } from '../../api/queries'
import type { DutySummary, TeacherOption } from '../../api/types'
import { Card } from '../../components/ui/Card'
import { cn } from '../../lib/cn'

export default function DutyCard() {
  const qc = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const { data: d } = useQuery({ queryKey: ['duties'], queryFn: fetchDuties })
  const { data: timetable } = useQuery({ queryKey: ['timetable'], queryFn: fetchActiveTimetable })
  const allTeachers: TeacherOption[] = (timetable?.teachers ?? []).map(t => ({ key: t.key, name: t.displayName }))

  const onSuccess = async (summary: DutySummary) => {
    qc.setQueryData(['duties'], summary)
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['candidates'] }),
      qc.invalidateQueries({ queryKey: ['day'] }),
    ])
  }
  const upload = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData()
      body.append('file', file)
      return api<DutySummary>('/duties', { method: 'POST', body })
    },
    onSuccess,
  })
  const resolve = useMutation({
    mutationFn: (v: { id: number; teacherKey: string }) =>
      api<DutySummary>(`/duties/${v.id}`, { method: 'PUT', body: JSON.stringify({ teacherKey: v.teacherKey }) }),
    onSuccess,
  })
  const skip = useMutation({
    mutationFn: (id: number) => api<DutySummary>(`/duties/${id}`, { method: 'DELETE' }),
    onSuccess,
  })
  const busy = resolve.isPending || skip.isPending

  return (
    <Card>
      <h2 className="mb-3 text-lg font-bold">Vahetunni valved</h2>
      <label
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl border-2 border-dashed border-casual-sky-blue-outline bg-sky-50 px-4 py-8 text-center transition-colors hover:bg-sky-100',
          upload.isPending && 'cursor-wait opacity-60',
        )}
      >
        <span aria-hidden className="text-3xl">📄</span>
        <span className="font-bold text-casual-sky-blue-darker">
          {upload.isPending ? 'Töötlen faili…' : 'Vali valvete Excel'}
        </span>
        <span className="text-sm text-text-caption">SVT, ÕVT graafik (.xlsx)</span>
        <input
          ref={input}
          type="file"
          accept=".xlsx"
          disabled={upload.isPending}
          onChange={e => {
            const f = e.target.files?.[0]
            e.target.value = '' // so the same file can be picked again after a failure
            if (f) upload.mutate(f)
          }}
          className="sr-only"
        />
      </label>

      {d && (
        <div className="mt-5 space-y-3">
          <p className="font-semibold">
            {d.fileName} · {new Date(d.uploadedAt).toLocaleDateString('et-EE')}
          </p>
          <div className="flex flex-wrap gap-2">
            <Stat value={d.dutyCount} label="valvet" />
            <Stat value={d.teacherCount} label="õpetajat" />
            {d.warnings.length > 0 && <Stat value={d.warnings.length} label="hoiatust" warn />}
          </div>
          {d.warnings.map((w, i) => (
            <p key={i} className="rounded-2xl bg-orange-50 px-4 py-2 text-sm text-casual-orange-darker">{w}</p>
          ))}

          {d.unresolved.length > 0 && (
            <div className="space-y-2 pt-2">
              <h3 className="font-bold">Vali õpetaja</h3>
              <p className="text-sm text-text-caption">
                Neid nimesid ei leitud tunniplaanist üheselt. Kuni õpetaja on valimata, valvet ei arvestata.
              </p>
              <ul className="divide-y divide-slate-100">
                {d.unresolved.map(u => {
                  const options = u.candidates.length > 0 ? u.candidates : allTeachers
                  return (
                    <li key={u.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                      <span className="font-medium">
                        {'ETKNR'[u.weekday - 1]} · {u.label} · {u.classGroup} · „{u.rawName}“
                      </span>
                      <span className="flex items-center gap-3">
                        <select
                          className="field min-h-11"
                          aria-label={`Vali õpetaja: ${u.rawName}`}
                          value=""
                          disabled={busy}
                          onChange={e => {
                            if (e.target.value) resolve.mutate({ id: u.id, teacherKey: e.target.value })
                          }}
                        >
                          <option value="">Vali õpetaja…</option>
                          {options.map(t => (
                            <option key={t.key} value={t.key}>{t.name}</option>
                          ))}
                        </select>
                        <button
                          onClick={() => skip.mutate(u.id)}
                          disabled={busy}
                          className="min-h-11 text-sm font-semibold text-text-caption hover:underline"
                        >
                          Jäta vahele
                        </button>
                      </span>
                    </li>
                  )
                })}
              </ul>
            </div>
          )}
        </div>
      )}
    </Card>
  )
}

function Stat({ value, label, warn }: { value: number; label: string; warn?: boolean }) {
  return (
    <span className={cn('rounded-2xl px-4 py-2', warn ? 'bg-orange-50 text-casual-orange-darker' : 'bg-slate-100')}>
      <span className="text-xl font-extrabold">{value}</span> <span className="text-sm">{label}</span>
    </span>
  )
}
