import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { fetchActiveTimetable } from '../api/queries'
import type { Day } from '../api/types'
import { Card } from '../components/ui/Card'
import { RealButton } from '../components/ui/RealButton'
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
    <div className="space-y-5">
      <div className="no-print space-y-5">
        <DateHeader date={date} />

        {weekend ? (
          <Notice emoji="🌤️">Nädalavahetusel tunde pole</Notice>
        ) : timetable.data === null ? (
          <Notice emoji="📄">
            Lae esmalt tunniplaan üles.{' '}
            <Link to="/tunniplaan" className="font-semibold text-casual-sky-blue-darker underline">Tunniplaani lehele</Link>
          </Notice>
        ) : (
          <>
            {timetable.data && (
              <AddAbsenceForm date={date} teachers={timetable.data.teachers} absentKeys={absentKeys} />
            )}

            {day.isLoading && <p className="text-text-caption">Laen…</p>}
            {day.data && absences.length === 0 && (
              <Notice emoji="✨">Sel päeval pole veel ühtegi puudujat.</Notice>
            )}
            {absences.length > 0 && <Progress day={day.data!} />}
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

  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <h1 className="text-4xl leading-none font-extrabold tracking-tight">
        <span className="bg-gradient-to-r from-casual-sky-blue-darker to-cyan-500 bg-clip-text text-transparent">
          {weekdayName(date)}
        </span>{' '}
        {shortDate(date)}
      </h1>
      <div className="flex items-center gap-2">
        <RealButton variant="light" size="icon" onClick={() => go(shiftSchoolDay(date, -1))} aria-label="Eelmine koolipäev">‹</RealButton>
        <input
          type="date"
          value={date}
          onChange={e => { if (isIsoDate(e.target.value)) go(e.target.value) }}
          aria-label="Kuupäev"
          className="field"
        />
        <RealButton variant="light" size="icon" onClick={() => go(shiftSchoolDay(date, 1))} aria-label="Järgmine koolipäev">›</RealButton>
        {date !== today && (
          <RealButton variant="sky" size="sm" onClick={() => go(today)}>Täna</RealButton>
        )}
      </div>
    </header>
  )
}

/** "7 / 10 tundi kaetud" with a bar, so it's obvious when the morning's work is done. */
function Progress({ day }: { day: Day }) {
  const rows = day.absences.flatMap(a => a.rows)
  const done = rows.filter(r => r.substituteKeys.length > 0 || r.freeText).length
  const pct = rows.length ? Math.round((done / rows.length) * 100) : 100
  const finished = done === rows.length

  return (
    <div className="flex items-center gap-4 rounded-3xl bg-white px-5 py-4 shadow-sm">
      <div className="flex-1">
        <p className="font-semibold">
          {finished ? 'Kõik tunnid on kaetud 🎉' : `${done} / ${rows.length} tundi kaetud`}
        </p>
        <div className="mt-2 h-3 overflow-hidden rounded-full bg-slate-100">
          <div
            className={finished ? 'h-full rounded-full bg-casual-green transition-all' : 'h-full rounded-full bg-casual-orange transition-all'}
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>
      <span className="text-2xl font-extrabold text-text-caption">{day.absences.length}<span className="ml-1 text-sm font-semibold">puudujat</span></span>
    </div>
  )
}

function Notice({ emoji, children }: { emoji: string; children: React.ReactNode }) {
  return (
    <Card className="flex items-center gap-4 text-lg">
      <span aria-hidden className="text-3xl">{emoji}</span>
      <p>{children}</p>
    </Card>
  )
}
