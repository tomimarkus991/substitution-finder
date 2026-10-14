import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Absence, Substitution } from '../../api/types'
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
    <section className="rounded-lg border border-gray-200 bg-white">
      <header className="flex items-center justify-between gap-2 border-b border-gray-100 py-1 pr-1 pl-4">
        <h2 className="font-semibold">
          {absence.teacherName}
          {partial && (
            <span className="ml-2 text-sm font-normal text-gray-600">
              tunnid {absence.fromPeriod}–{absence.toPeriod}
            </span>
          )}
        </h2>
        <button
          onClick={() => { if (confirm('Eemaldada puuduja ja tema asendused?')) remove.mutate() }}
          disabled={remove.isPending}
          className="min-h-11 rounded px-3 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
        >
          Eemalda
        </button>
      </header>

      {absence.rows.length === 0 ? (
        <p className="px-4 py-3 text-gray-600">Sel päeval tunde pole</p>
      ) : (
        <ul className="divide-y divide-gray-100">
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

/** The text that goes into the table's "Tunni asendaja nimi" column. */
const assignment = (row: Substitution) => [row.substituteNames, row.freeText].filter(Boolean).join(' / ')

type RowProps = { date: string; row: Substitution; open: boolean; onToggle: () => void; onClose: () => void }

function Row({ date, row, open, onToggle, onClose }: RowProps) {
  const text = assignment(row)
  return (
    <li className={`border-l-4 ${text ? 'border-l-green-500' : 'border-l-amber-400'}`}>
      <div className="flex flex-col gap-2 px-3 py-2 sm:flex-row sm:items-center">
        <div className="min-w-0 flex-1">
          <p>
            <strong>{row.period}. tund</strong> · {row.classes} · {subjectName(row.subject)}
          </p>
          <p className={text ? 'font-medium text-gray-900' : 'text-gray-400'}>{text || 'Asendaja valimata'}</p>
        </div>
        <button
          onClick={onToggle}
          aria-expanded={open}
          className={`min-h-11 w-full rounded px-4 font-medium sm:w-auto ${
            open ? 'bg-gray-200 text-gray-900' : text ? 'border border-gray-300 bg-white text-gray-900' : 'bg-blue-600 text-white'
          }`}
        >
          {open ? 'Sulge' : text ? 'Muuda' : 'Vali asendaja'}
        </button>
      </div>
      {open && <CandidatePicker date={date} row={row} onClose={onClose} />}
    </li>
  )
}
