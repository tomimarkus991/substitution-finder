import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { TimetablePreview } from '../../api/types'
import { Card } from '../../components/ui/Card'
import { RealButton } from '../../components/ui/RealButton'
import { cn } from '../../lib/cn'

export default function UploadCard() {
  const qc = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const upload = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData()
      body.append('file', file)
      return api<TimetablePreview>('/timetables', { method: 'POST', body })
    },
  })
  const activate = useMutation({
    mutationFn: (id: number) => api<unknown>(`/timetables/${id}/activate`, { method: 'POST' }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['timetable'] })
      upload.reset()
      if (input.current) input.current.value = ''
    },
  })
  const p = upload.data

  return (
    <Card>
      <h2 className="mb-3 text-lg font-bold">Lae üles uus tunniplaan</h2>
      <label
        className={cn(
          'flex cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl border-2 border-dashed border-casual-sky-blue-outline bg-sky-50 px-4 py-8 text-center transition-colors hover:bg-sky-100',
          upload.isPending && 'cursor-wait opacity-60',
        )}
      >
        <span aria-hidden className="text-3xl">📄</span>
        <span className="font-bold text-casual-sky-blue-darker">
          {upload.isPending ? 'Töötlen faili…' : 'Vali tunniplaani PDF'}
        </span>
        <span className="text-sm text-text-caption">õpetajate tunniplaan</span>
        <input
          ref={input}
          type="file"
          accept="application/pdf"
          disabled={upload.isPending}
          onChange={e => {
            const f = e.target.files?.[0]
            e.target.value = '' // so the same file can be picked again after a failure
            if (f) upload.mutate(f)
          }}
          className="sr-only"
        />
      </label>

      {p && (
        <div className="mt-5 space-y-3">
          <p className="font-semibold">{p.fileName}</p>
          <div className="flex flex-wrap gap-2">
            <Stat value={p.teacherCount} label="õpetajat" />
            <Stat value={p.lessonCount} label="tundi" />
            {p.warnings.length > 0 && <Stat value={p.warnings.length} label="hoiatust" warn />}
          </div>
          {p.addedTeachers.length > 0 && <p><span className="font-semibold">Uued:</span> {p.addedTeachers.join(', ')}</p>}
          {p.removedTeachers.length > 0 && <p><span className="font-semibold">Eemaldatud:</span> {p.removedTeachers.join(', ')}</p>}
          {p.warnings.map((w, i) => (
            <p key={i} className="rounded-2xl bg-orange-50 px-4 py-2 text-sm text-casual-orange-darker">{w}</p>
          ))}
          {p.active ? (
            <p className="font-semibold text-casual-green-darker">✓ See tunniplaan on juba aktiivne</p>
          ) : (
            <RealButton
              variant="green"
              size="sm"
              className="w-full sm:w-auto"
              onClick={() => activate.mutate(p.id)}
              disabled={activate.isPending}
            >
              Aktiveeri
            </RealButton>
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
