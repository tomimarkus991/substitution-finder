<!-- generated from 953ce3a65125, 2026-10-23 -->
<!-- c4-map config abedddd81c0a -->

> **Agent context map.** Dense on purpose and not laid out for viewing; labels may overlap when rendered. Diagrams for people: [docs/architecture/](../architecture/).

# Level 1: System context

Asendus helps one school's substitution coordinator cover absent teachers. Sign-in is Google-only and limited to an email allowlist (`ALLOWED_EMAILS`). The timetable and duty schedules come in as files the coordinator uploads; Asendus has no live link to aSc.

```mermaid
C4Context
  title System context: Asendus

  Person(coordinator, "Substitution coordinator", "Plans daily cover, in Estonian UI")
  System(asendus, "Asendus", "Finds substitutes, builds the day's table")
  System_Ext(google, "Google Identity", "Sign-in and ID token keys")
  System_Ext(asc, "aSc Timetables", "School timetable software")

  Rel(coordinator, asendus, "Uploads timetables and duties, marks absences, picks substitutes")
  Rel(coordinator, asc, "Exports teacher timetable PDF from")
  Rel(asendus, google, "Signs users in with", "OIDC")
```

- Inputs: aSc teacher-timetable PDF (README), duty schedule Excel `.xlsx` (`DutyExcelParser`).
- Output: the day's substitution table, copied or printed by the coordinator (README).
