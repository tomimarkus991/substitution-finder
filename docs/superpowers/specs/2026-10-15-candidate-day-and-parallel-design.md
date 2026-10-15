# Candidate day strip, parallel teachers, teacher renames — design

## Goal

Feedback from Aili after using the day page:

1. **Day strip.** When she picks a substitute she checks that teacher's day in the timetable PDF: how many gaps
   they have, and whether taking this lesson makes e.g. six lessons in a row. The picker should show that.
2. **Parallel teachers ("grupp pooleks").** German is taught to two classes by three teachers at once. When one
   is absent she wants to split the group between the other two. They teach in that period, so the picker
   hides them today and she can't select them (free text holds one name, not keys).
3. **Renames.** Some names from the PDF are wrong (a stray "in" before a first name) and a placeholder
   ("inka õps") is now a real person. She wants to edit the display name.

The three parts are independent. Build order: 1 → 2 → 3, one commit series each.

## 1. Day strip on candidate buttons

### API

`CandidateDto` gets two fields:

```java
record CandidateDto(String key, String name, Availability status, List<String> subjects, int todayCount,
                    List<String> duties, List<DaySlot> day, int streak, String parallelLesson) {}

record DaySlot(int period, SlotKind kind, String label) {}
enum SlotKind { LESSON, SUBSTITUTING, DUTY, EMPTY }
```

- `day` always has 7 entries (periods 1–7) for the candidate's weekday:
  - `LESSON` — timetable lesson; `label` = `"<classes> · <subject>"` (subject code as stored). A period can
    hold several lessons (split cells); their labels are joined with `"; "` in lesson order.
  - `SUBSTITUTING` — already assigned on some substitution row that day in that period; `label` = the row's
    `"<classes> · <subject>"` (for a duty row: its `dutyLabel`).
  - `SUBSTITUTING` with several rows in one period: labels joined with `"; "`.
  - `DUTY` — a break duty in that period and none of the above; `label` = `DutyLabels.label(duty)` (joined
    with `"; "` if several).
  - `EMPTY` — none; `label` = `""`.
  - Priority when several apply: `LESSON` > `SUBSTITUTING` > `DUTY`. A duty in a lesson period is still
    reported through the existing `duties` field only for the requested period, as today.
- `streak` = length of the run of consecutive occupied periods (`LESSON` or `SUBSTITUTING`) that contains the
  requested period, counting the requested period as occupied. Duties don't count (they're breaks, not lessons).
  Example: lessons 1,2,4,5 and the request is period 3 → streak 5.
- Computed in `SubstitutionService.candidates()` from data it already loads (`lessonsOn`, `rowsOf`,
  `dutiesOn`); a small pure helper `DayStrip.of(lessons, substitutions, duties, period)` does the work and is
  unit-tested.
- Ordering of candidates is unchanged in this part (part 2 extends it).

### UI (`CandidateButton`)

- Under the name: 7 small squares (≈14px, gap 2px) numbered 1–7 underneath in caption size.
  - `LESSON` — slate fill; `SUBSTITUTING` — sky-blue fill; `DUTY` — violet outline; `EMPTY` — white with a light
    border. The requested period additionally gets ★ and a thicker dark border on top of whatever its kind's
    fill is (for a `PARALLEL` teacher that is their own lesson, so ★ on slate).
  - Each square has `title` = `"<period>. <label>"` (or `"<period>. vaba"`); hover shows it on desktop.
- Right of the strip: `→ N järjest`. Shown in orange bold when `N >= LONG_STREAK` (constant, 5), caption
  colour otherwise.
- A one-line legend with an `InfoTip` under the groups, like the existing duty legend.
- Selected teachers (the "Valitud" group) keep their plain toggle; no strip there.

## 2. Parallel teachers

### Matching

- A row's classes string uses `/` between classes (`TimetablePdfParser.joinClasses`, e.g.
  `2.3/2.4/2.5/2.3V/2.5V`). Split on `/`, trim, drop empty, compare tokens exactly (case-insensitive).
- A teacher is **parallel** for a row when, on that weekday, they have a lesson in the row's period whose class
  tokens intersect the row's class tokens, and they aren't blocked (absent in the period, already substituting
  in the period, or a placeholder).
- Duty rows (`dutyLabel != null`) never have parallel teachers.

### API

- `GET /days/{date}/candidates?period=P&row=R` — new optional `row` (substitution id). Without it, behaviour is
  unchanged (no `PARALLEL`). With it, the row must exist (else 404), and its period must equal `P` and its
  absence's date must equal `date` (else 400).
- `Availability` gets `PARALLEL`. `Eligibility.of` stays as is; parallel detection is a separate check applied
  to teachers `Eligibility` returned `HIDDEN` for *because they teach in p*.
- `parallelLesson` on the DTO = `"<classes> · <subject>"` of their lesson(s) in that period (joined with `"; "`
  if several); `null` otherwise.
- Order: `CANDIDATE_ORDER` becomes a comparator built per request, since it depends on the row:
  1. status (enum order `FREE`, `ASK_FIRST`, `PARALLEL`),
  2. same subject — `true` when any of their lessons in the period has the row's subject (only differs among
     `PARALLEL`; `false` for everyone else and when no `row` is given), `true` first,
  3. has a break duty in the period → after the others (as today),
  4. `todayCount`, 5. name in `DISPLAY_ORDER` (as today).
- `PUT /substitutions/{id}` needs no change: it already accepts several keys and doesn't re-check eligibility.

### UI (`CandidatePicker`)

- Query key becomes `['candidates', date, row.period, row.id]` and the URL passes `row`. Invalidations on
  `['candidates', date]` keep working (prefix match).
- `web/src/api/types.ts`: `Candidate.status` gains `'PARALLEL'`; add `day`, `streak`, `parallelLesson` and the
  `DaySlot` type.
- New group after "Küsi enne", only when non-empty: title **"Sama klass samal ajal"**, hint
  "grupp pooleks", dot in teal (sky blue already means "selected" and `SUBSTITUTING`), `InfoTip`: "Neil on
  samal tunnil tund samale klassile (nt teine keelerühm). Vali üks või mitu, kui jagad puuduja rühma nende
  vahel."
- `CandidateButton` gets a third tone for `PARALLEL` (teal border/background, matching the dot) instead of
  falling into the orange branch.
- Their button shows `parallelLesson` instead of the subject list, plus the day strip.

## 3. Renaming teachers

### Data

```sql
-- V4__teacher_name_override.sql
CREATE TABLE teacher_name_override (
    teacher_key  TEXT PRIMARY KEY,
    display_name TEXT NOT NULL CHECK (length(trim(display_name)) > 0)
);
```

Keyed by teacher key like `placeholder_flag`, so it survives new timetable uploads (as long as the PDF name and
so the key stays the same).

### API

- `PUT /teachers/{key}/name` body `{ "displayName": "Denis Kask" }` (`@Size(max = 100)`). The value is
  NFC-normalised, trimmed and inner whitespace collapsed; blank deletes the override (back to the PDF name).
  404 if the key isn't in the active version. Returns the new `TeacherGridDto`.
- `TimetableService.displayName(Teacher)` — the override if present, else `TeacherKey.displayName(name)`. For
  lists, a `Map<String, String> overrides()` loaded once per request. Every current
  `TeacherKey.displayName(t.getName())` call in `TimetableService` (`active`, `grid`), `SubstitutionService`
  (`createAbsence`, `nameFor`, `candidates`) and `DutyService` (`toDuty`, `resolve`, `summary`) uses it, so new
  snapshots take the overridden name. Sorting by display name uses the overridden name. Exception: the static
  `difference()` in the upload preview (`TimetableService`) keeps PDF names — it compares two PDFs.
- `TeacherGridDto` keeps `name` (PDF name) and `displayName` (effective) and adds `renamed: boolean` and
  `pdfDisplayName` (the PDF name as "Firstname Surname", for the "PDF-is: …" line).
  `TeacherSummaryDto` gets the effective `displayName` only (no new field).
- Duty matching (`DutyService`, surname → teacher): a teacher is reachable by their PDF surname and, when
  renamed, also by the override's surname (last word). Each surname group is deduplicated by teacher key, so
  a teacher whose old and new surname fold to the same string isn't counted twice.
- Matching only runs on upload, so saving a name also calls `DutyService.rematch(teacherKey)`.
  `DutyService` already depends on `TimetableService`, so to avoid a circular dependency a small
  `TeacherRenameService` (in `timetable`) depends on both and runs `timetableService.rename(key, name)` then
  `dutyService.rematch(key)` in one `@Transactional` method; the controller calls it. `rematch`:
  - unresolved duties (`teacher_key IS NULL`) of the current duty version are matched again by `raw_name`
    with the rules above; one match → set key and name and clear `candidate_keys`; otherwise the candidate
    keys are recomputed;
  - matched duties of that teacher get the new `teacher_name`.
- Stored snapshots on absences and substitution rows (`absence.teacher_name`, `substitution.substitute_names`)
  are not rewritten: past days keep the name they had.

### UI (timetable page, selected teacher)

- A ✎ button next to the teacher's name in the grid header. It turns the name into a text field (prefilled
  with the display name); Enter or blur saves, Escape cancels. When `renamed`, a caption line shows
  "PDF-is: <PDF display name>" and a "Taasta" link that saves blank.
- After save, invalidate `['timetable']`-prefixed queries and `['candidates']`, `['day']`, `['duties']`.

## Testing

- `DayStripTest` (unit): priority of kinds, streak across lessons and substitutions, duty not counting, request
  at the edges (period 1, period 7).
- `EligibilityTest` unchanged; a `ParallelTest` (unit) for class-token matching (`/` split, trailing `/`, case).
- `DayControllerTest` (integration): `PARALLEL` appears only with `row`, contains teachers sharing a class in that
  period, excludes blocked ones, same-subject first; 400 on period mismatch; `day`/`streak` on a known teacher
  from the fixture PDF.
- `DayControllerTest`: `row` from another date → 400.
- Timetable integration test: rename → summary, grid and candidates show the new name; blank restores; a duty
  left unresolved at upload is matched after the rename without re-uploading the Excel; a teacher whose old
  and new surname are the same isn't ambiguous; 404 on unknown key.
- Web: existing tests keep passing; no new pure logic on the client.

## Out of scope

- Hover popover with the full day (the strip's `title` covers it).
- Editing names of teachers not in the active version.
- Fuzzy class matching (e.g. treating `2.3V` as part of `2.3`): exact tokens first; revisit if Aili misses
  someone.
