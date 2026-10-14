import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import type { Day } from '../api/types'
import { isIsoDate, isWeekend, shiftSchoolDay, shortDate, todayIso, weekdayName } from '../lib/dates'
import AddAbsenceForm from './day/AddAbsenceForm'
import AbsenceCard from './day/AbsenceCard'
import DayTable from './day/DayTable'

export default function DayPage() {
  const { date } = useParams()
  if (!isIsoDate(date)) return <Navigate to={`/paev/${todayIso()}`} replace />
  // Keyed by date so the open picker resets when the day changes.
  return <DayView key={date} date={date} />
}

function DayView({ date }: { date: string }) {
  const weekend = isWeekend(date)
  const timetable = useQuery({ queryKey: ['timetable'], queryFn: fetchActiveTimetable })
  const day = useQuery({
    queryKey: ['day', date],
    queryFn: () => api<Day>(`/days/${date}`),
    enabled: !weekend,
  })
  const [openRow, setOpenRow] = useState<number | null>(null)

  const absences = day.data?.absences ?? []
  const absentKeys = new Set(absences.map(a => a.teacherKey))

  return (
    <div className="space-y-4">
      <div className="no-print space-y-4">
        <DateHeader date={date} />

        {weekend ? (
          <Notice>Nädalavahetusel tunde pole</Notice>
        ) : timetable.data === null ? (
          <Notice>
            Lae esmalt tunniplaan üles.{' '}
            <Link to="/tunniplaan" className="font-medium text-blue-700 underline">Tunniplaani lehele</Link>
          </Notice>
        ) : (
          <>
            {timetable.data && (
              <AddAbsenceForm date={date} teachers={timetable.data.teachers} absentKeys={absentKeys} />
            )}

            {day.isLoading && <p className="text-sm text-gray-600">Laen…</p>}
            {day.data && absences.length === 0 && (
              <p className="text-gray-600">Sel päeval pole veel ühtegi puudujat.</p>
            )}
            {absences.map(a => (
              <AbsenceCard key={a.id} date={date} absence={a} openRow={openRow} onOpenRow={setOpenRow} />
            ))}
          </>
        )}
      </div>

      {day.data && !weekend && <DayTable day={day.data} />}
    </div>
  )
}

function DateHeader({ date }: { date: string }) {
  const navigate = useNavigate()
  const go = (d: string) => navigate(`/paev/${d}`)
  const today = todayIso()
  const navButton = 'flex min-h-11 min-w-11 items-center justify-center rounded border border-gray-300 bg-white text-xl hover:bg-gray-50'

  return (
    <header className="space-y-2">
      <h1 className="text-2xl font-semibold">
        {weekdayName(date)} {shortDate(date)}
      </h1>
      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => go(shiftSchoolDay(date, -1))} className={navButton} aria-label="Eelmine koolipäev">‹</button>
        <input
          type="date"
          value={date}
          onChange={e => { if (isIsoDate(e.target.value)) go(e.target.value) }}
          aria-label="Kuupäev"
          className="min-h-11 rounded border border-gray-300 bg-white px-3"
        />
        <button onClick={() => go(shiftSchoolDay(date, 1))} className={navButton} aria-label="Järgmine koolipäev">›</button>
        {date !== today && (
          <button onClick={() => go(today)} className="min-h-11 rounded px-3 text-blue-700 hover:underline">
            Täna
          </button>
        )}
      </div>
    </header>
  )
}

function Notice({ children }: { children: React.ReactNode }) {
  return <p className="rounded-lg border border-gray-200 bg-white p-4 text-gray-700">{children}</p>
}
