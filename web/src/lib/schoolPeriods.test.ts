import { describe, expect, it } from 'vitest'
import { activePreset, halfYearStart, schoolYearStart } from './schoolPeriods'

describe('schoolYearStart', () => {
  it('is this September from September on', () => {
    expect(schoolYearStart('2026-09-01')).toBe('2026-09-01')
    expect(schoolYearStart('2026-11-24')).toBe('2026-09-01')
  })
  it('is last September before September', () => {
    expect(schoolYearStart('2027-01-15')).toBe('2026-09-01')
    expect(schoolYearStart('2027-08-31')).toBe('2026-09-01')
  })
})

describe('halfYearStart', () => {
  it('is 1 September in the first half-year, January included', () => {
    expect(halfYearStart('2026-11-24')).toBe('2026-09-01')
    expect(halfYearStart('2027-01-31')).toBe('2026-09-01')
  })
  it('is 1 February in the second half-year, summer included', () => {
    expect(halfYearStart('2027-02-01')).toBe('2027-02-01')
    expect(halfYearStart('2027-06-10')).toBe('2027-02-01')
    expect(halfYearStart('2027-08-20')).toBe('2027-02-01')
  })
})

describe('activePreset', () => {
  const presets = [
    { label: 'See poolaasta', range: { from: '2026-09-01', to: '2026-11-24' } },
    { label: 'See õppeaasta', range: { from: '2026-09-01', to: '2026-11-24' } },
  ]
  const same = { from: '2026-09-01', to: '2026-11-24' }

  it('lights only the pressed one when two presets cover the same days', () => {
    expect(activePreset(presets, same, 'See õppeaasta')).toBe('See õppeaasta')
    expect(activePreset(presets, same, 'See poolaasta')).toBe('See poolaasta')
  })
  it('lights the first match when the range came from the calendar', () => {
    expect(activePreset(presets, same, null)).toBe('See poolaasta')
  })
  it('lights nothing when the range matches no preset', () => {
    expect(activePreset(presets, { from: '2026-10-01', to: '2026-10-31' }, 'See poolaasta')).toBeNull()
    expect(activePreset(presets, { from: null, to: null }, null)).toBeNull()
  })
})
