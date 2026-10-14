# Break duties (ÕVT / SVT) — design

## Goal

The school keeps break-duty rosters in an Excel file ("SVT, ÕVT, muud graafikud"). An outdoor break (ÕVT) or
canteen break (SVT) for the younger classes happens *during* a lesson period, so a teacher who is free in
period 3 by the timetable can still be on yard duty 10:45–11:15. Aili uploads the Excel and the app:

1. warns about candidates who have a duty in the period being filled, and
2. lists the duties of an absent teacher on the day page so a cover can be written in.

Out of scope: the swimming escorts (Ujumine), afternoon group (Pikapäevarühm), teaching assistants (Õp.abid),
hallway duty ("Fuajee", during breaks between lessons), "Ujumas kaasas".

## The Excel (first sheet, "alates 7.09")

Five day blocks. A block starts with a header row: column A is the weekday letter `E T K N R`, columns C/D/E are
`ÕVT SVT ÕVT`, F and H name the class groups, G and I are `pos`. The rows below (until the next header or an
empty B) are one class group each (`B` = "I klassid" … "VI klassid"):

| Col | Meaning | Examples |
|---|---|---|
| C | ÕVT time for groups whose E is empty | `10:25-11:05`, `12:15-12:55`, `10:25` |
| D | SVT (canteen) start | `10:25`, `12:10` |
| E | ÕVT time range, or a non-duty note | `10:45-11:15`, `UJUMAS 3.1-3.2`, `klj 12:30-13:15` |
| F, H | Duty teachers (surnames) for the two class halves | `Kask, Tamm`, `sööklas: Mänd, Saar`, `Lepp // 4.s klj tund (315)`, `Kuusk (õvt +6.S)`, `4.S & 6.R ÕVT - Vaher` |
| G, I | Yard position for F / H | `4`, `5` |

Other columns (J onwards) are notes and are ignored.

## Reading rules (`DutyExcelParser`, Apache POI)

- **Blocks:** a header row has column A equal to one of `E T K N R` (→ weekday 1–5) and C equal to `ÕVT`. Other A
  values (`võõrkeelte koostöö aeg`, `klassijuhatajatund`) are labels and ignored. A block ends at the next header or
  at a row with an empty B. Merged cells only hold their value in the top-left cell, which is the cell we read.
- **Cell text:** every cell is read as text, NFC-normalised and trimmed. Numeric Excel times (column D is usually
  one) are formatted as `H:MM`.
- **Time range:** regex `(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})` (the sheet has `12:55- 13:20`,
  `12:35 -13:10`).
- Per group row and per half (F/G, H/I):
  - Text starting with `sööklas:` is an **SVT** duty from the D time. Canteen duty lasts 45 minutes, like a
    lesson (`SVT_MINUTES`). `sööklas: klassijuhataja` has no name → skipped with a warning.
  - Otherwise it is an **ÕVT** duty. Time = the first range in E, unless E starts with `klj` or `UJUMAS`; then the
    range in C. No range (e.g. C is only `10:25`, or empty) → warning, half skipped.
- **Names**, in this order: strip a leading `sööklas:`; cut at `//`; drop `(…)`; if ` - ` remains, take what follows
  it; split on `,` `&` `/`; trim each token. Each token is a surname.
- **Never drop text silently:** if the part cut off after `//` or inside `(…)` contains a time range or `õvt`
  (case-insensitive), add a warning quoting the cell (row 31 hides a second ÕVT duty there).
- A surname matches a teacher in the **active timetable** when it equals the first word of the aSc name (aSc
  prints "Surname Firstname"), ignoring case and diacritics. Expected misses in the real data: a surname the PDF spells differently
  (a letter the PDF font lacks), and teaching assistants who aren't in the teacher timetable. `duty.teacher_key` is that teacher's existing key (`TeacherKey.of`).
- **No match or several matches:** the duty is still saved, with `teacher_key` null, the surname in `raw_name`, and
  (for several matches) the matching keys in `candidate_keys`. It is listed as *unresolved* on Tunniplaan, where Aili
  picks the teacher (see Web). Unresolved duties have no effect until resolved. Re-uploading the same Excel resolves
  names fresh; earlier picks are not carried over.
- Warnings name the day and group, e.g. `K, IV klassid: "Lepp // 4.s klj tund (315)" – õuevahetunni kellaaeg puudub`.
- Not an xlsx, or no day header found → 400 `See ei tundu olevat vahetunni valvete tabel`.

## Periods

`PeriodTimes` holds the bell schedule from the timetable PDF:
1 8:45–9:30, 2 9:40–10:25, 3 10:35–11:20, 4 11:30–12:15, 5 12:25–13:10, 6 13:20–14:05, 7 14:15–15:00.
Each duty belongs to **one** period: the one it overlaps most (`min(end, p.end) - max(start, p.start)`, ties → the
earlier period). This matches how the school sees it: the eating/outdoor break fills the teacher's empty slot in
the timetable, including the 10-minute breaks around it. Examples: ÕVT 10:45–11:15 → 3, 11:05–11:30 → 3,
12:55–13:20 → 5, SVT 12:10–12:55 → 5. The duty label still shows the real times.

## Data

```sql
CREATE TABLE duty_version (id, uploaded_at, uploaded_by → app_user, file_name, warnings TEXT[]);
-- only the latest upload is kept: an upload deletes the old version (cascades to its duties)
CREATE TABLE duty (id, version_id → duty_version ON DELETE CASCADE, weekday 1–5, period 1–7, kind 'OVT'|'SVT',
                   starts TIME, ends TIME, class_group TEXT, position TEXT, teacher_key TEXT NULL, teacher_name TEXT NULL,
                   raw_name TEXT, candidate_keys TEXT[] NOT NULL DEFAULT '{}');
ALTER TABLE substitution ADD COLUMN duty_label TEXT;   -- null for lesson rows
```

Duties link to teachers by `teacher_key`, so they survive a new timetable upload. A new duty upload replaces the
previous one at once (no separate activate step: the unresolved list is the preview). Uploading needs an active
timetable (409 `Lae esmalt tunniplaan üles`).

## API

- `POST /duties` (multipart xlsx) → `{ fileName, uploadedAt, dutyCount, teacherCount, warnings, unresolved }`.
  `unresolved` = `[{ id, weekday, label, classGroup, rawName, candidates: [{key, name}] }]` (candidates empty when
  nothing matched).
- `GET /duties` → the same summary for the active version (204 when none).
- `PUT /duties/{id} { teacherKey }` (PUT, not PATCH: CORS allows GET/POST/PUT/DELETE) → resolves one unresolved duty to a teacher of the active timetable (400
  otherwise); returns the updated summary.
- `DELETE /duties/{id}` → drops one unresolved duty ("Jäta vahele", e.g. a teaching assistant); returns the summary.
- `CandidateDto` gains `duties: List<String>` — labels like `ÕVT 10:45–11:15` of that teacher's duties in the
  requested period. `CANDIDATE_ORDER` becomes: status, then has-duty-in-this-period (false first), then
  todayCount, then name.
- Creating an absence also adds a substitution row per duty of that teacher on that weekday whose period is inside `fromPeriod..toPeriod`: `period` = the duty's period, `classes` = class group,
  `subject` = `"ÕVT"`/`"SVT"`, `duty_label` = `ÕVT 10:45–11:15 · pos 4`. It is edited like any row (substitutes /
  free text), shows in Ajalugu and print, and sorts after the lesson rows of its period (ROW_ORDER falls back to id).
  The `Substitution` entity and `SubstitutionDto` (and `toDto`) gain `dutyLabel`. A teacher picked for a duty row
  counts as substituting in its `period` only (existing `substitutingIn`).

## Web

- **Tunniplaan:** a second dashed upload area "Vahetunni valved (Excel)" under the timetable upload, with stat pills
  (duties, teachers) and the warning list. Below it, **"Vali õpetaja"**: one line per unresolved duty, e.g.
  `K · ÕVT 10:45–11:15 · I klassid · "Kask"`, with a select. Several matches → those teachers; no match → all active teachers in name order. Picking saves
  immediately. A "Jäta vahele" text action drops the duty.
- **Candidate tiles:** a violet badge per duty label (orange already means "Küsi enne"); the tile stays clickable.
- **Day page rows:** duty rows show the label instead of the lesson text, with a violet accent.
- **Help:** a `DutyHelp` section on Õpetus and in the Tunniplaan help panel; an InfoTip on the duty badge.

## Testing

- The repo is public, so no test uses the school's Excel or a real name from it. `TestDutySheet` builds an xlsx
  in memory with the real layout (time cells, numeric positions, side notes) and made-up surnames.
- Parser: a made-up sheet with every tricky pattern (spaced ranges, `sööklas:`, `//`, `(…)`, ` - `, `klj`, `UJUMAS`,
  a single time) yields the expected duties and warnings. Unit tests for each name-cleaning step and period overlap.
  The real file is checked once by hand, locally, without committing it.
- Integration (surnames taken from the timetable PDF fixture at runtime): upload → candidates carry duty labels and sort; absence creation adds duty rows; a new timetable
  upload keeps duties; an ambiguous surname is saved unresolved, PUT resolves it, and only then does it affect
  candidates.
- Web: candidate badge and duty row rendering, upload card.

## Decided

- Canteen duty lasts 45 minutes.
- No known duplicate surnames; if one appears (or a name doesn't match), Aili picks the teacher on Tunniplaan.
