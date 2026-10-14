import { useEffect, useRef, useState } from 'react'
import type { Day } from '../../api/types'
import { shortDate } from '../../lib/dates'
import { buildRows, copyTable, HEADER, toPlainText } from '../../lib/dayTable'

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

  const button = 'min-h-11 rounded border border-gray-300 bg-white px-4 hover:bg-gray-50'
  const th = 'border border-black px-2 py-1 text-left font-semibold'
  const td = 'border border-black px-2 py-1'

  return (
    <section className="space-y-3">
      <div className="no-print flex flex-wrap items-center gap-2">
        <h2 className="mr-auto text-xl font-semibold">Asenduste tabel</h2>
        <button onClick={copy} className={button}>Kopeeri</button>
        <button onClick={() => window.print()} className={button}>Prindi</button>
        {copied && <span role="status" className="text-green-700">Kopeeritud</span>}
        {failed && <span role="alert" className="text-red-700">Kopeerimine ebaõnnestus</span>}
      </div>
      <h2 className="print-only text-xl font-semibold">Asendused {shortDate(day.date)}</h2>
      <div className="overflow-x-auto">
        <table className="border-collapse text-sm">
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
