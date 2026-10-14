import { describe, expect, it } from 'vitest'
import { shortDate, weekdayName, isWeekend, isIsoDate, shiftSchoolDay, longDate } from './dates'

describe('dates', () => {
  it('formats d.mm like her table', () => expect(shortDate('2026-09-04')).toBe('4.09'))
  it('names weekdays in Estonian', () => expect(weekdayName('2026-09-04')).toBe('Reede'))
  it('formats weekday with full date', () => expect(longDate('2026-09-04')).toBe('Reede 4.09.2026'))
  it('detects weekends', () => {
    expect(isWeekend('2026-09-05')).toBe(true)
    expect(isWeekend('2026-09-04')).toBe(false)
  })
  it('skips weekends when moving between school days', () => {
    expect(shiftSchoolDay('2026-09-04', 1)).toBe('2026-09-07')
    expect(shiftSchoolDay('2026-09-07', -1)).toBe('2026-09-04')
    expect(shiftSchoolDay('2026-09-05', -1)).toBe('2026-09-04')
    expect(shiftSchoolDay('2026-12-31', 1)).toBe('2027-01-01')
  })
  it('validates ISO dates', () => {
    expect(isIsoDate('2026-09-04')).toBe(true)
    expect(isIsoDate('2026-02-30')).toBe(false)
    expect(isIsoDate('4.09')).toBe(false)
    expect(isIsoDate(undefined)).toBe(false)
  })
})
