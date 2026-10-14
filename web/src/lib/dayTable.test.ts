import { describe, expect, it } from 'vitest'
import { buildRows, toHtml, toPlainText } from './dayTable'
import type { Day } from '../api/types'

const day: Day = {
  date: '2026-09-04', weekday: 5,
  absences: [
    { id: 2, teacherKey: 'randmaa janell', teacherName: 'Janell Randmaa', fromPeriod: 1, toPeriod: 7, rows: [
      { id: 21, period: 5, classes: '2.3', subject: 'Ek', substituteKeys: ['utt merike'], substituteNames: 'Merike Utt', freeText: '', dutyLabel: null },
      { id: 20, period: 2, classes: '2.3', subject: 'Ek', substituteKeys: ['kiik raina'], substituteNames: 'Raina Kiik', freeText: '', dutyLabel: null },
    ]},
    { id: 1, teacherKey: 'pärnpuu taimi', teacherName: 'Taimi Pärnpuu', fromPeriod: 1, toPeriod: 7, rows: [
      { id: 10, period: 2, classes: '4R', subject: 'Ek', substituteKeys: ['põldma ragnar', 'rentel aivi'], substituteNames: 'Ragnar Põldma / Aivi Rentel', freeText: '', dutyLabel: null },
      { id: 11, period: 6, classes: '6M', subject: 'Aj', substituteKeys: [], substituteNames: '', freeText: 'Koju', dutyLabel: null },
      { id: 12, period: 4, classes: '4D', subject: 'Lo', substituteKeys: ['hirv eleriin'], substituteNames: 'Eleriin Hirv', freeText: 'tulevad hiljem', dutyLabel: null },
    ]},
  ],
}

describe('day table', () => {
  it('sorts by absent teacher then period and fills the columns like her Word table', () => {
    expect(buildRows(day)).toEqual([
      ['4.09', 'Janell Randmaa', '2.3', '2', 'Raina Kiik', 'eesti keel'],
      ['4.09', 'Janell Randmaa', '2.3', '5', 'Merike Utt', 'eesti keel'],
      ['4.09', 'Taimi Pärnpuu', '4R', '2', 'Ragnar Põldma / Aivi Rentel', 'eesti keel'],
      ['4.09', 'Taimi Pärnpuu', '4D', '4', 'Eleriin Hirv / tulevad hiljem', 'loodus'],
      ['4.09', 'Taimi Pärnpuu', '6M', '6', 'Koju', 'ajalugu'],
    ])
  })

  it('puts a duty label in the subject column', () => {
    const d: Day = { date: '2026-09-04', weekday: 5, absences: [
      { id: 3, teacherKey: 'x y', teacherName: 'X Y', fromPeriod: 1, toPeriod: 7, rows: [
        { id: 31, period: 3, classes: 'I klassid', subject: 'ÕVT', substituteKeys: [], substituteNames: '', freeText: '', dutyLabel: 'ÕVT 10:45–11:15 · pos 4' },
        { id: 30, period: 2, classes: '2.3', subject: 'Ma', substituteKeys: [], substituteNames: '', freeText: '', dutyLabel: null },
      ]},
    ]}
    const rows = buildRows(d)
    expect(rows[0][5]).toBe('matemaatika')
    expect(rows[1][2]).toBe('I klassid')
    expect(rows[1][5]).toBe('ÕVT 10:45–11:15 · pos 4')
  })

  it('renders an HTML table with the header and escapes text', () => {
    const html = toHtml([['4.09', 'A <b>', '1', '1', '', 'x']])
    expect(html).toContain('>Puuduva õpetaja nimi</th>')
    expect(html).toContain('A &lt;b&gt;')
    expect(html.startsWith('<table')).toBe(true)
  })

  it('renders tab-separated plain text', () => {
    expect(toPlainText([['4.09', 'A', '1', '1', 'B', 'x']]).split('\n')[1]).toBe('4.09\tA\t1\t1\tB\tx')
  })
})
