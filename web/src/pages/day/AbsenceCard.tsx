import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Absence, Substitution } from '../../api/types'
import { RealButton } from '../../components/ui/RealButton'
import { cn } from '../../lib/cn'
import { subjectName } from '../../lib/subjects'
import CandidatePicker from './CandidatePicker'

type Props = {
  date: string
  absence: Absence
  openRow: number | null
  onOpenRow: (id: number | null) => void
}

export default function AbsenceCard({ date, absence, openRow, onOpenRow }: Props) {
  const qc = useQueryClient()
  const remove = useMutation({
    mutationFn: () => api<void>(`/absences/${absence.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['day', date] })
      qc.invalidateQueries({ queryKey: ['candidates', date] })
    },
  })
  const partial = absence.fromPeriod !== 1 || absence.toPeriod !== 7

  return (
    <section className="overflow-hidden rounded-3xl bg-white shadow-sm">
      <header className="flex items-center gap-3 px-5 pt-5 pb-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-casual-red text-lg font-bold text-white">
          {initials(absence.teacherName)}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-lg font-bold">{absence.teacherName}</h2>
          <p className="text-sm text-text-caption">
            {partial ? `puudub ${absence.fromPeriod}.–${absence.toPeriod}. tunnini` : 'puudub terve päeva'}
          </p>
        </div>
        <button
          onClick={() => { if (confirm('Eemaldada puuduja ja tema asendused?')) remove.mutate() }}
          disabled={remove.isPending}
          className="min-h-11 px-2 text-sm font-semibold text-casual-red-darker hover:underline disabled:opacity-50"
        >
          Eemalda
        </button>
      </header>

      {absence.rows.length === 0 ? (
        <p className="px-5 pb-5 text-text-caption">Sel päeval tunde pole</p>
      ) : (
        <ul className="space-y-2 px-3 pb-3">
          {absence.rows.map(row => (
            <Row
              key={row.id}
              date={date}
              row={row}
              open={openRow === row.id}
              onToggle={() => onOpenRow(openRow === row.id ? null : row.id)}
              onClose={() => onOpenRow(null)}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

const initials = (name: string) =>
  name.split(/[\s-]+/).filter(Boolean).map(p => p[0]).slice(0, 2).join('').toUpperCase()

/** The text that goes into the table's "Tunni asendaja nimi" column. */
const assignment = (row: Substitution) => [row.substituteNames, row.freeText].filter(Boolean).join(' / ')

type RowProps = { date: string; row: Substitution; open: boolean; onToggle: () => void; onClose: () => void }

function Row({ date, row, open, onToggle, onClose }: RowProps) {
  const text = assignment(row)
  return (
    <li className={cn(
      'overflow-hidden rounded-2xl border-2',
      open ? 'border-casual-sky-blue-outline' : 'border-slate-100',
      row.dutyLabel && 'border-l-8 border-l-violet-500',
    )}>
      <div className="flex flex-col gap-3 p-3 sm:flex-row sm:items-center">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <span
            className={cn(
              'flex h-12 w-12 shrink-0 flex-col items-center justify-center rounded-2xl leading-none font-extrabold text-white',
              text ? 'bg-casual-green' : 'bg-casual-orange',
            )}
          >
            <span className="text-xl">{row.period}</span>
            <span className="text-[0.6rem] font-bold tracking-wide uppercase">tund</span>
          </span>
          <div className="min-w-0">
            <p className="font-semibold">
              {row.classes} <span className="font-normal text-text-caption">· {row.dutyLabel ?? subjectName(row.subject)}</span>
            </p>
            <p className={cn('truncate', text ? 'font-semibold text-casual-green-darker' : 'text-text-caption')}>
              {text || 'Asendaja valimata'}
            </p>
          </div>
        </div>
        <RealButton
          onClick={onToggle}
          aria-expanded={open}
          size="sm"
          variant={open ? 'dark' : text ? 'light' : 'sky'}
          className="w-full sm:w-auto"
        >
          {open ? 'Sulge' : text ? 'Muuda' : 'Vali asendaja'}
        </RealButton>
      </div>
      {open && <CandidatePicker date={date} row={row} onClose={onClose} />}
    </li>
  )
}
