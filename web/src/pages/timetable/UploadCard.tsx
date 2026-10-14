import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { TimetablePreview } from '../../api/types'

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
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">Lae üles uus tunniplaan</h2>
      <input
        ref={input}
        type="file"
        accept="application/pdf"
        disabled={upload.isPending}
        onChange={e => { const f = e.target.files?.[0]; if (f) upload.mutate(f) }}
        className="block w-full text-sm file:mr-3 file:rounded file:border-0 file:bg-gray-100 file:px-3 file:py-2"
      />
      {upload.isPending && <p className="mt-3 text-sm text-gray-600">Töötlen faili…</p>}
      {p && (
        <div className="mt-4 space-y-2 text-sm">
          <p className="font-medium">{p.fileName}: {p.teacherCount} õpetajat, {p.lessonCount} tundi</p>
          {p.addedTeachers.length > 0 && <p>Uued: {p.addedTeachers.join(', ')}</p>}
          {p.removedTeachers.length > 0 && <p>Eemaldatud: {p.removedTeachers.join(', ')}</p>}
          {p.warnings.map((w, i) => (
            <p key={i} className="rounded bg-amber-50 px-3 py-2 text-amber-800">{w}</p>
          ))}
          {p.active ? (
            <p className="text-green-700">See tunniplaan on juba aktiivne.</p>
          ) : (
            <button
              onClick={() => activate.mutate(p.id)}
              disabled={activate.isPending}
              className="w-full rounded bg-blue-600 px-4 py-2 font-medium text-white disabled:opacity-50 sm:w-auto"
            >
              Aktiveeri
            </button>
          )}
        </div>
      )}
    </section>
  )
}
