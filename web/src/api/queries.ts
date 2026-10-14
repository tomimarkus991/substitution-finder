import { api, ApiError } from './client'
import type { DutySummary, TimetableSummary } from './types'

/** The active timetable, or null when none has been uploaded yet (404). */
export async function fetchActiveTimetable(): Promise<TimetableSummary | null> {
  try {
    return await api<TimetableSummary>('/timetables/active')
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null
    throw e
  }
}

/** The uploaded break duties, or null when none have been uploaded yet (204). */
export async function fetchDuties(): Promise<DutySummary | null> {
  return (await api<DutySummary | undefined>('/duties')) ?? null
}
