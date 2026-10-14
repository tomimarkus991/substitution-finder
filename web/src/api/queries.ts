import { api, ApiError } from './client'
import type { TimetableSummary } from './types'

/** The active timetable, or null when none has been uploaded yet (404). */
export async function fetchActiveTimetable(): Promise<TimetableSummary | null> {
  try {
    return await api<TimetableSummary>('/timetables/active')
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null
    throw e
  }
}
