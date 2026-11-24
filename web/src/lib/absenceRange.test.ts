import { describe, expect, it } from 'vitest'
import type { Substitution } from '../api/types'
import { lostCovers } from './absenceRange'

const row = (period: number, substituteNames = '', freeText = ''): Substitution => ({
  id: period, period, classes: '5D', subject: 'Lo', substituteKeys: [], substituteNames, freeText, dutyLabel: null,
})

describe('lostCovers', () => {
  const rows = [row(2), row(3, 'Lea Teesalu'), row(6, '', 'Koju'), row(7)]

  it('lists filled rows that fall outside the new range', () =>
    expect(lostCovers(rows, 2, 5).map(r => r.period)).toEqual([6]))
  it('ignores empty rows outside the range', () => expect(lostCovers(rows, 1, 6)).toEqual([]))
  it('counts rows before the new start too', () =>
    expect(lostCovers(rows, 4, 5).map(r => r.period)).toEqual([3, 6]))
  it('is empty when widening', () => expect(lostCovers(rows, 1, 7)).toEqual([]))
})
