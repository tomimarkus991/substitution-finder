import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import UploadCard from './timetable/UploadCard'
import TeacherGrid from './timetable/TeacherGrid'

export default function TimetablePage() {
  const qc = useQueryClient()
  const [selected, setSelected] = useState<string | null>(null)
  const { data: active, isLoading } = useQuery({ queryKey: ['timetable'], queryFn: fetchActiveTimetable })
  const setPlaceholder = useMutation({
    mutationFn: (v: { key: string; placeholder: boolean }) =>
      api<void>(`/teachers/${encodeURIComponent(v.key)}/placeholder`, {
        method: 'PUT',
        body: JSON.stringify({ placeholder: v.placeholder }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['timetable'] }),
  })

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Tunniplaan</h1>
      <UploadCard />
      {isLoading && <p className="text-sm text-gray-600">Laen…</p>}
      {active === null && <p className="text-gray-600">Tunniplaani pole veel üles laetud</p>}
      {active && (
        <section className="rounded-lg border border-gray-200 bg-white p-4">
          <h2 className="font-semibold">Aktiivne tunniplaan</h2>
          <p className="mb-3 text-sm text-gray-600">
            {active.fileName}, üles laetud {new Date(active.uploadedAt).toLocaleDateString('et-EE')}
          </p>
          <ul className="divide-y divide-gray-100">
            {active.teachers.map(t => (
              <li key={t.key} className="flex flex-wrap items-center justify-between gap-2 py-1">
                <button
                  onClick={() => setSelected(t.key)}
                  className={`min-h-11 text-left hover:underline ${selected === t.key ? 'font-semibold' : ''}`}
                >
                  {t.displayName} <span className="text-sm text-gray-500">({t.lessonCount})</span>
                </button>
                <label className="flex min-h-11 items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={t.placeholder}
                    disabled={setPlaceholder.isPending}
                    onChange={e => setPlaceholder.mutate({ key: t.key, placeholder: e.target.checked })}
                  />
                  Pole päris õpetaja
                </label>
              </li>
            ))}
          </ul>
        </section>
      )}
      {selected && <TeacherGrid teacherKey={selected} />}
    </div>
  )
}
