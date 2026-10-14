import { describe, expect, it } from 'vitest'
import { SUBJECTS, subjectName } from './subjects'

// Every distinct lesson.subject found in the three sample PDFs.
const CODES = ['Aj', 'Digikirjaoskus', 'E2', 'Ek', 'Ettevõtlus/digi', 'Ettevõtlusõpe', 'Ink', 'InÕ', 'KKp', 'KKt',
  'Kirjandus', 'Kk', 'Ku', 'Kunst/digi', 'Lo', 'Ma', 'Mu', 'Sk', 'Tehnol/digi', 'TÕ', 'matem/digi', 'ujumine', 'Üh']

describe('subjectName', () => {
  it.each(CODES)('maps %s', code => expect(Object.keys(SUBJECTS)).toContain(code))
  it('maps codes to full names', () => expect(subjectName('Ek')).toBe('eesti keel'))
  it('keeps unknown codes', () => expect(subjectName('Xyz')).toBe('Xyz'))
})
