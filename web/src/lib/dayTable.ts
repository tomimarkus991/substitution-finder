import type { Day } from '../api/types'
import { shortDate } from './dates'
import { subjectName } from './subjects'

export const HEADER = ['Kuupäev', 'Puuduva õpetaja nimi', 'Klass', 'Mitmes tund', 'Tunni asendaja nimi', 'Õppeaine']
export type TableRow = string[]

export function buildRows(day: Day): TableRow[] {
  const date = shortDate(day.date)
  return [...day.absences]
    .sort((a, b) => a.teacherName.localeCompare(b.teacherName, 'et'))
    .flatMap(a => [...a.rows].sort((x, y) => x.period - y.period).map(r => [
      date, a.teacherName, r.classes, String(r.period),
      [r.substituteNames, r.freeText].filter(Boolean).join(' / '),
      subjectName(r.subject),
    ]))
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const cell = 'border:1px solid #000;padding:4px 8px'

export function toHtml(rows: TableRow[]): string {
  const head = HEADER.map(h => `<th style="${cell}">${esc(h)}</th>`).join('')
  const body = rows.map(r => `<tr>${r.map(c => `<td style="${cell}">${esc(c)}</td>`).join('')}</tr>`).join('')
  return `<table style="border-collapse:collapse"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
}

export const toPlainText = (rows: TableRow[]) => [HEADER, ...rows].map(r => r.join('\t')).join('\n')

export async function copyTable(rows: TableRow[]) {
  await navigator.clipboard.write([new ClipboardItem({
    'text/html': new Blob([toHtml(rows)], { type: 'text/html' }),
    'text/plain': new Blob([toPlainText(rows)], { type: 'text/plain' }),
  })])
}
