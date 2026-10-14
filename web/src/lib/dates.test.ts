import { describe, expect, it } from 'vitest'
import { shortDate, weekdayName, isWeekend } from './dates'

describe('dates', () => {
  it('formats d.mm like her table', () => expect(shortDate('2026-09-04')).toBe('4.09'))
  it('names weekdays in Estonian', () => expect(weekdayName('2026-09-04')).toBe('Reede'))
  it('detects weekends', () => {
    expect(isWeekend('2026-09-05')).toBe(true)
    expect(isWeekend('2026-09-04')).toBe(false)
  })
})
