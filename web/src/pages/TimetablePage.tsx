import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import { Card } from '../components/ui/Card'
import { PageTitle } from '../components/ui/PageTitle'
import { cn } from '../lib/cn'
import InfoTip from '../help/InfoTip'
import UploadCard from './timetable/UploadCard'
import TeacherGrid from './timetable/TeacherGrid'

export default function TimetablePage() {
  const qc = useQueryClient()
  const [selected, setSelected] = useState<string | null>(null)
  const gridRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (selected) gridRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [selected])
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
    <div className="space-y-5">
      <PageTitle hint="Lae üles kooli õpetajate tunniplaani PDF. Uus tunniplaan hakkab kehtima, kui vajutad Aktiveeri.">
        Tunniplaan
      </PageTitle>
      <UploadCard />
      {isLoading && <p className="text-text-caption">Laen…</p>}
      {active === null && (
        <Card className="flex items-center gap-4 text-lg">
          <span aria-hidden className="text-3xl">📄</span>
          <p>Tunniplaani pole veel üles laetud</p>
        </Card>
      )}
      {selected && <div ref={gridRef} className="scroll-mt-20"><TeacherGrid teacherKey={selected} /></div>}

      {active && (
        <Card>
          <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-bold">Aktiivne tunniplaan</h2>
            <p className="text-sm text-text-caption">
              {active.fileName} · {new Date(active.uploadedAt).toLocaleDateString('et-EE')} · {active.teachers.length} õpetajat
            </p>
          </div>
          <ul className="divide-y divide-slate-100">
            {active.teachers.map(t => (
              <li key={t.key} className="flex flex-wrap items-center justify-between gap-2 py-1">
                <button
                  onClick={() => setSelected(t.key)}
                  className={cn(
                    'min-h-11 text-left hover:underline',
                    selected === t.key ? 'font-bold text-casual-sky-blue-darker' : 'font-medium',
                    t.placeholder && 'text-text-caption',
                  )}
                >
                  {t.displayName}
                  <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-text-caption">
                    {t.lessonCount} tundi
                  </span>
                </button>
                <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm text-text-caption">
                  <input
                    type="checkbox"
                    checked={t.placeholder}
                    disabled={setPlaceholder.isPending}
                    onChange={e => setPlaceholder.mutate({ key: t.key, placeholder: e.target.checked })}
                    className="h-5 w-5 accent-casual-sky-blue-darker"
                  />
                  Pole päris õpetaja
                  <InfoTip label="Pole päris õpetaja">
                    Näiteks „Puutöö“ või „Inglise keel“, kui õpetajat veel pole. Neid saab puudujaks märkida, aga asendajana ei pakuta.
                  </InfoTip>
                </label>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}
