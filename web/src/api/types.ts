export interface AuthResponse {
  token: string
  user: { id: number; email: string; name: string }
}

// Timetable
export interface TimetablePreview {
  id: number
  fileName: string
  active: boolean
  teacherCount: number
  lessonCount: number
  addedTeachers: string[]
  removedTeachers: string[]
  warnings: string[]
}
export interface TeacherSummary {
  key: string
  name: string
  displayName: string
  placeholder: boolean
  lessonCount: number
}
export interface TimetableSummary {
  id: number
  fileName: string
  uploadedAt: string
  teachers: TeacherSummary[]
}
export interface Lesson {
  weekday: number
  period: number
  classes: string
  subject: string
  room: string
  note: string
}
export interface TeacherGrid {
  key: string
  name: string
  displayName: string
  /** The PDF's name as "Firstname Surname". */
  pdfDisplayName: string
  renamed: boolean
  placeholder: boolean
  lessons: Lesson[]
}

// Days
export interface Substitution {
  id: number
  period: number
  classes: string
  subject: string
  substituteKeys: string[]
  substituteNames: string
  freeText: string
  dutyLabel: string | null
}
export interface Absence {
  id: number
  teacherKey: string
  teacherName: string
  fromPeriod: number
  toPeriod: number
  rows: Substitution[]
}
export interface Day {
  date: string
  weekday: number
  absences: Absence[]
}
export type SlotKind = 'LESSON' | 'SUBSTITUTING' | 'DUTY' | 'EMPTY'
export interface DaySlot {
  period: number
  kind: SlotKind
  label: string
}
export interface Candidate {
  key: string
  name: string
  status: 'FREE' | 'ASK_FIRST' | 'PARALLEL'
  subjects: string[]
  todayCount: number
  duties: string[]
  day: DaySlot[]
  streak: number
  parallelLesson: string | null
}

// Break duties
export interface TeacherOption { key: string; name: string }
export interface UnresolvedDuty {
  id: number
  weekday: number
  label: string
  classGroup: string
  rawName: string
  candidates: TeacherOption[]
}
export interface DutySummary {
  fileName: string
  uploadedAt: string
  dutyCount: number
  teacherCount: number
  warnings: string[]
  unresolved: UnresolvedDuty[]
}
