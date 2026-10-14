import { useEffect, useRef, useState } from 'react'
import type { Day } from '../../api/types'
import { shortDate } from '../../lib/dates'
import { buildRows, copyTable, HEADER, toPlainText } from '../../lib/dayTable'
import { RealButton } from '../../components/ui/RealButton'

export default function DayTable({ day }: { day: Day }) {
  const rows = buildRows(day)
  const [copied, setCopied] = useState(false)
  const [failed, setFailed] = useState(false)
  const timer = useRef<number | undefined>(undefined)
  useEffect(() => () => window.clearTimeout(timer.current), [])

  if (rows.length === 0) return null

  const confirm = (ok: boolean) => {
    setCopied(ok)
    setFailed(!ok)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => { setCopied(false); setFailed(false) }, 2500)
  }

  const copy = async () => {
    try {
      await copyTable(rows)
      confirm(true)
    } catch {
      try {
        await navigator.clipboard.writeText(toPlainText(rows))
        confirm(true)
      } catch {
        confirm(false)
      }
    }
  }

  // Soft on screen, plain black borders on paper
  const th = 'border-b-2 border-slate-200 bg-slate-50 px-3 py-2 text-left font-bold print:border print:border-black print:bg-white'
  const td = 'border-b border-slate-100 px-3 py-2 print:border print:border-black'

  return (
    <section className="space-y-4 rounded-3xl bg-white p-5 shadow-sm print:rounded-none print:p-0 print:shadow-none">
      <div className="no-print flex flex-wrap items-center gap-2">
        <h2 className="mr-auto text-lg font-bold">Asenduste tabel</h2>
        {copied && <span role="status" className="font-semibold text-casual-green-darker">✓ Kopeeritud</span>}
        {failed && <span role="alert" className="font-semibold text-casual-red-darker">Kopeerimine ebaõnnestus</span>}
        <RealButton variant="sky" size="sm" onClick={copy}>Kopeeri</RealButton>
        <RealButton variant="light" size="sm" onClick={() => window.print()}>Prindi</RealButton>
      </div>
      <h2 className="print-only text-xl font-semibold">Asendused {shortDate(day.date)}</h2>
      <div className="overflow-x-auto rounded-2xl border border-slate-200 print:rounded-none print:border-0">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr>{HEADER.map(h => <th key={h} className={th}>{h}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i}>{r.map((c, j) => <td key={j} className={td}>{c}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
