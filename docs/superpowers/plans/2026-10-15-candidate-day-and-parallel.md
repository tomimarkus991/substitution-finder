# Candidate Day Strip, Parallel Teachers, Renames Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers-extended-cc:subagent-driven-development (if subagents available) or superpowers-extended-cc:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Candidate buttons show the teacher's day (7 slots + "N järjest"); teachers teaching the same class in the same period can be picked to split a group; teacher display names can be edited.

**Architecture:** Three independent parts. (1) A pure `DayStrip` helper builds 7 slots per candidate from data `SubstitutionService.candidates()` already loads. (2) `candidates()` takes an optional row id; teachers whose own lesson in that period shares a class token with the row become `PARALLEL`. (3) A `teacher_name_override` table keyed by teacher key; `TimetableService.displayName` replaces the direct `TeacherKey.displayName(t.getName())` calls; a `TeacherRenameService` saves the name and re-matches break duties.

**Tech Stack:** Spring Boot 4 / Java 25 / Postgres / Flyway, React 19 + TanStack Query + Tailwind, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-15-candidate-day-and-parallel-design.md` — read it first.

**Conventions (from this repo):**
- Run API tests: `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q test -Dtest=<Class>` (Docker must be running for integration tests). Full: `./mvnw verify`.
- Run web tests: `cd web && npm test -- --run`; types: `npx tsc -b`; lint: `npm run lint`.
- All user-facing text is Estonian. Errors are `ResponseStatusException`.
- Commit with the `commit` skill (subject `type: Capitalised three+ words`, last body line `Changelog: Feature`/`Fix`/…). No Co-authored-by.
- The repo is **public**: never write a real teacher name into code. Integration tests take names/keys from the fixture PDF at runtime (as `DayControllerTest` does); made-up names only otherwise. (`Pärnpuu Taimi` already appears in existing tests and may be reused.)
- Colours: green = Vaba, orange = Küsi enne, violet = duty, sky blue = selected / substituting, **teal = parallel (new)**.

---

## Part 1 — Day strip

### Task 1: `DayStrip` helper

**Files:**
- Create: `api/src/main/java/com/asendus/api/substitution/SlotKind.java`
- Create: `api/src/main/java/com/asendus/api/substitution/DayStrip.java`
- Modify: `api/src/main/java/com/asendus/api/substitution/DayDtos.java` (add `DaySlot`)
- Test: `api/src/test/java/com/asendus/api/substitution/DayStripTest.java`

- [ ] **Step 1: Add the types**

`SlotKind.java`:
```java
package com.asendus.api.substitution;

/** What a teacher does in one period of the day, for the strip on candidate buttons. */
public enum SlotKind { LESSON, SUBSTITUTING, DUTY, EMPTY }
```

In `DayDtos`, next to `CandidateDto`:
```java
    /** One period of a candidate's day. label: "4R · Ek" (several joined with "; "), a duty label, or "". */
    public record DaySlot(int period, SlotKind kind, String label) {}
```

- [ ] **Step 2: Write the failing test**

```java
package com.asendus.api.substitution;

import com.asendus.api.duty.Duty;
import com.asendus.api.substitution.DayDtos.DaySlot;
import com.asendus.api.timetable.Lesson;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static com.asendus.api.substitution.SlotKind.*;
import static org.assertj.core.api.Assertions.assertThat;

class DayStripTest {

    static Lesson lesson(int period, String classes, String subject) {
        return Lesson.builder().period((short) period).classes(classes).subject(subject).room("").note("").build();
    }

    static Substitution sub(int period, String classes, String subject, String dutyLabel) {
        return Substitution.builder().period((short) period).classes(classes).subject(subject).dutyLabel(dutyLabel).build();
    }

    static Duty duty(int period) {
        return Duty.builder().period((short) period).kind("OVT")
                .starts(LocalTime.of(10, 45)).ends(LocalTime.of(11, 15)).classGroup("I klassid").rawName("X").build();
    }

    @Test void sevenSlotsWithKindsAndLabels() {
        List<DaySlot> day = DayStrip.of(
                List.of(lesson(1, "4R", "Ek"), lesson(2, "5D", "Lo")),
                List.of(sub(4, "6R", "Aj", null)),
                List.of(duty(5)));
        assertThat(day).extracting(DaySlot::period).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(day).extracting(DaySlot::kind).containsExactly(LESSON, LESSON, EMPTY, SUBSTITUTING, DUTY, EMPTY, EMPTY);
        assertThat(day.get(0).label()).isEqualTo("4R · Ek");
        assertThat(day.get(2).label()).isEmpty();
        assertThat(day.get(3).label()).isEqualTo("6R · Aj");
        assertThat(day.get(4).label()).isEqualTo("ÕVT 10:45–11:15");
    }

    @Test void lessonBeatsSubstitutionBeatsDuty() {
        List<DaySlot> day = DayStrip.of(List.of(lesson(3, "7A", "Ma")),
                List.of(sub(3, "8B", "Ek", null), sub(4, "8B", "Ek", null)), List.of(duty(3), duty(4)));
        assertThat(day.get(2).kind()).isEqualTo(LESSON);
        assertThat(day.get(3).kind()).isEqualTo(SUBSTITUTING);
    }

    @Test void severalInOnePeriodAreJoined() {
        List<DaySlot> day = DayStrip.of(List.of(lesson(1, "6S", "KKt"), lesson(1, "3.1", "ujumine")),
                List.of(sub(2, "I klassid", "ÕVT", "ÕVT 10:45–11:15 · pos 4")), List.of());
        assertThat(day.get(0).label()).isEqualTo("6S · KKt; 3.1 · ujumine");
        assertThat(day.get(1).label()).isEqualTo("ÕVT 10:45–11:15 · pos 4"); // a duty row shows its duty label
    }

    @Test void blankPartsAreLeftOut() {
        assertThat(DayStrip.label("4R", "")).isEqualTo("4R");
        assertThat(DayStrip.label("", "Ek")).isEqualTo("Ek");
    }

    @Test void streakCountsLessonsAndSubstitutionsAroundThePeriod() {
        // lessons 1,2,4,5; substituting 6; duty 7; asking about 3 → 1..6
        List<DaySlot> day = DayStrip.of(
                List.of(lesson(1, "a", "x"), lesson(2, "a", "x"), lesson(4, "a", "x"), lesson(5, "a", "x")),
                List.of(sub(6, "b", "y", null)), List.of(duty(7)));
        assertThat(DayStrip.streak(day, 3)).isEqualTo(6);
    }

    @Test void streakAtTheEdgesAndAlone() {
        List<DaySlot> day = DayStrip.of(List.of(lesson(2, "a", "x"), lesson(6, "a", "x")), List.of(), List.of());
        assertThat(DayStrip.streak(day, 1)).isEqualTo(2);
        assertThat(DayStrip.streak(day, 7)).isEqualTo(2);
        assertThat(DayStrip.streak(day, 4)).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q test -Dtest=DayStripTest`
Expected: compilation failure, `DayStrip` not found.

- [ ] **Step 4: Implement**

```java
package com.asendus.api.substitution;

import com.asendus.api.duty.Duty;
import com.asendus.api.duty.DutyLabels;
import com.asendus.api.substitution.DayDtos.DaySlot;
import com.asendus.api.timetable.Lesson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A teacher's day as one slot per period, and how many periods in a row they'd work. */
public final class DayStrip {
    public static final int PERIODS = 7;
    private static final String JOIN = "; ";

    private DayStrip() {}

    /** Lessons beat substitutions, which beat break duties; several of one kind in a period are joined with "; ". */
    public static List<DaySlot> of(List<Lesson> lessons, List<Substitution> substituting, List<Duty> duties) {
        List<DaySlot> out = new ArrayList<>(PERIODS);
        for (int p = 1; p <= PERIODS; p++) {
            int period = p;
            String lesson = joined(lessons.stream().filter(l -> l.getPeriod() == period)
                    .map(l -> label(l.getClasses(), l.getSubject())));
            String sub = joined(substituting.stream().filter(s -> s.getPeriod() == period)
                    .map(s -> s.getDutyLabel() != null ? s.getDutyLabel() : label(s.getClasses(), s.getSubject())));
            String duty = joined(duties.stream().filter(d -> d.getPeriod() == period).map(DutyLabels::label));
            if (lesson != null) out.add(new DaySlot(p, SlotKind.LESSON, lesson));
            else if (sub != null) out.add(new DaySlot(p, SlotKind.SUBSTITUTING, sub));
            else if (duty != null) out.add(new DaySlot(p, SlotKind.DUTY, duty));
            else out.add(new DaySlot(p, SlotKind.EMPTY, ""));
        }
        return out;
    }

    /** Length of the run of lessons and substitutions that contains the period, counting the period itself. */
    public static int streak(List<DaySlot> day, int period) {
        int from = period;
        int to = period;
        while (from > 1 && busy(day.get(from - 2))) from--;
        while (to < PERIODS && busy(day.get(to))) to++;
        return to - from + 1;
    }

    /** "4R · Ek"; blank parts are left out. */
    public static String label(String classes, String subject) {
        return Stream.of(classes, subject).filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" · "));
    }

    private static boolean busy(DaySlot s) {
        return s.kind() == SlotKind.LESSON || s.kind() == SlotKind.SUBSTITUTING;
    }

    private static String joined(Stream<String> labels) {
        List<String> list = labels.toList();
        return list.isEmpty() ? null : String.join(JOIN, list);
    }
}
```

- [ ] **Step 5: Run the test** — same command. Expected: PASS (6 tests).

- [ ] **Step 6: Commit** (`feat: Add day strip helper`, `Changelog: Feature`).

---

### Task 2: Candidates carry `day` and `streak`

**Files:**
- Modify: `api/src/main/java/com/asendus/api/substitution/DayDtos.java` (`CandidateDto`)
- Modify: `api/src/main/java/com/asendus/api/substitution/SubstitutionService.java` (`candidates`)
- Test: `api/src/test/java/com/asendus/api/substitution/DayControllerTest.java`

- [ ] **Step 1: Write the failing integration test** (add to the candidates section of `DayControllerTest`)

```java
    @Test
    void candidatesCarryTheirDay() throws Exception {
        // Pärnpuu Taimi on Friday: periods 1 (4R Ek), 2, 3, 4, 6 → free in 5, so 1..6 in a row
        String taimi = "pärnpuu taimi";
        candidates(5)
                .andExpect(jsonPath(byKey(taimi) + ".day[*].period", contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath(byKey(taimi) + ".day[0].kind", contains("LESSON")))
                .andExpect(jsonPath(byKey(taimi) + ".day[0].label", contains("4R · Ek")))
                .andExpect(jsonPath(byKey(taimi) + ".day[4].kind", contains("EMPTY")))
                .andExpect(jsonPath(byKey(taimi) + ".streak", contains(6)));

        // She takes someone's period-7 lesson → slot 7 is SUBSTITUTING and the run grows to 7
        String other = fridayTeachers(ps -> ps.contains(7)).stream().filter(k -> !k.equals(taimi)).findFirst().orElseThrow();
        String absence = absent(other, 7, 7);
        putSubstitution(id(absence, "$.rows[0].id"), "{\"substituteKeys\":[\"" + taimi + "\"],\"freeText\":\"\"}")
                .andExpect(status().isOk());
        candidates(5)
                .andExpect(jsonPath(byKey(taimi) + ".day[6].kind", contains("SUBSTITUTING")))
                .andExpect(jsonPath(byKey(taimi) + ".streak", contains(7)));
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q test -Dtest=DayControllerTest#candidatesCarryTheirDay`
Expected: FAIL, no `day` in the JSON.

- [ ] **Step 3: Extend the DTO**

```java
    /**
     * duties: labels of the candidate's break duties in the requested period. day: their 7 periods.
     * streak: periods in a row they'd work if they take this one.
     */
    public record CandidateDto(String key, String name, Availability status, List<String> subjects, int todayCount,
                               List<String> duties, List<DaySlot> day, int streak) {}
```

- [ ] **Step 4: Build the strip in `candidates()`**

After `Map<String, List<Duty>> duties = dutyService.dutiesOn(weekday);` add:
```java
        Map<String, List<Substitution>> substituting = substitutingByKey(rows);
```
and replace the `out.add(...)` line with:
```java
                List<DaySlot> day = DayStrip.of(dayLessons, substituting.getOrDefault(key, List.of()),
                        duties.getOrDefault(key, List.of()));
                out.add(new CandidateDto(key, TeacherKey.displayName(teacher.getName()), status,
                        subjects.getOrDefault(key, List.of()), todayCounts.getOrDefault(key, 0), dutyLabels,
                        day, DayStrip.streak(day, period)));
```
Add the helper next to `todayCounts`:
```java
    /** Per teacher key, the rows they substitute on that day. */
    private static Map<String, List<Substitution>> substitutingByKey(List<Substitution> rows) {
        Map<String, List<Substitution>> out = new HashMap<>();
        for (Substitution r : rows) {
            for (String key : r.getSubstituteKeys()) out.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return out;
    }
```
Import `com.asendus.api.substitution.DayDtos.DaySlot`.

- [ ] **Step 5: Run `DayControllerTest` and `DutyDayTest`** — Expected: all PASS.

- [ ] **Step 6: Commit** (`feat: Return day strip with candidates`).

---

### Task 3: Day strip on candidate buttons (web)

**Files:**
- Modify: `web/src/api/types.ts`
- Create: `web/src/pages/day/DayStrip.tsx`
- Modify: `web/src/pages/day/CandidatePicker.tsx`
- Modify: `web/src/help/guideSections.tsx` (`SubstituteHelp`)

- [ ] **Step 1: Types**

```ts
export type SlotKind = 'LESSON' | 'SUBSTITUTING' | 'DUTY' | 'EMPTY'
export interface DaySlot {
  period: number
  kind: SlotKind
  label: string
}
export interface Candidate {
  key: string
  name: string
  status: 'FREE' | 'ASK_FIRST'
  subjects: string[]
  todayCount: number
  duties: string[]
  day: DaySlot[]
  streak: number
}
```

- [ ] **Step 2: `DayStrip.tsx`**

```tsx
import type { DaySlot, SlotKind } from '../../api/types'
import { cn } from '../../lib/cn'

/** From this many periods in a row the count turns orange. */
export const LONG_STREAK = 5

export const SLOT_STYLE: Record<SlotKind, string> = {
  LESSON: 'border-slate-400 bg-slate-400 text-white',
  SUBSTITUTING: 'border-casual-sky-blue bg-casual-sky-blue text-white',
  DUTY: 'border-violet-500 bg-white text-slate-900',
  EMPTY: 'border-slate-200 bg-white text-slate-900',
}

type Props = { day: DaySlot[]; period: number; streak: number }

/** The candidate's 7 periods; ★ marks the one being filled. Hovering a square shows its class and subject. */
export default function DayStrip({ day, period, streak }: Props) {
  return (
    <span className="mt-1.5 flex items-end gap-2">
      <span className="flex gap-0.5">
        {day.map(s => (
          <span key={s.period} className="flex flex-col items-center">
            <span
              title={`${s.period}. ${s.label || 'vaba'}`}
              className={cn(
                'flex h-4 w-4 items-center justify-center rounded border-2 text-[9px] leading-none',
                SLOT_STYLE[s.kind],
                s.period === period && 'border-slate-900 ring-2 ring-slate-900',
              )}
            >
              {s.period === period && '★'}
            </span>
            <span className="text-[10px] leading-tight text-text-caption">{s.period}</span>
          </span>
        ))}
      </span>
      <span className={cn('pb-3 text-xs', streak >= LONG_STREAK ? 'font-bold text-casual-orange-darker' : 'text-text-caption')}>
        → {streak} järjest
      </span>
    </span>
  )
}
```

- [ ] **Step 3: Use it in `CandidateButton`** — pass the period down:

In `CandidatePicker`, render `<CandidateButton key={c.key} candidate={c} period={row.period} onClick={...} />` in both groups. In `CandidateButton`:
```tsx
function CandidateButton({ candidate: c, period, onClick }: { candidate: Candidate; period: number; onClick: () => void }) {
```
and right after the name span:
```tsx
      <DayStrip day={c.day} period={period} streak={c.streak} />
```

- [ ] **Step 4: Legend** — after the duty legend `<p>` (inside `candidates.data && (...)`), add:
```tsx
          {others.length > 0 && (
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-caption">
              <Swatch kind="LESSON" /> tund
              <Swatch kind="SUBSTITUTING" /> asendab
              <Swatch kind="DUTY" /> valve
              <Swatch kind="EMPTY" /> vaba
              <span>★ see tund</span>
              <InfoTip label="Päeva riba">
                Ruudud on õpetaja selle päeva 1.–7. tund. „→ N järjest“ näitab, mitu tundi järjest tal tuleks, kui ta selle
                tunni võtab. Alates {LONG_STREAK}-st on see oranž.
              </InfoTip>
            </p>
          )}
```
with, at the bottom of the file:
```tsx
function Swatch({ kind }: { kind: SlotKind }) {
  return <span className={cn('inline-block h-3 w-3 rounded border-2', SLOT_STYLE[kind])} />
}
```
Imports: `DayStrip, { LONG_STREAK, SLOT_STYLE } from './DayStrip'` and `SlotKind` type.

- [ ] **Step 5: Guide** — in `SubstituteHelp`, after the paragraph that ends "…et koormus jaguks ühtlaselt. …oma tekst.", add:
```tsx
      <p>
        Iga nime all on tema päev: hallid ruudud on tunnid, sinised juba võetud asendused, lillad valved ja ★ on see
        tund. <b>→ N järjest</b> näitab, mitu tundi järjest tal tuleks. Alates viiest on see oranž. Hiirega ruudu peal
        näed klassi ja ainet.
      </p>
```

- [ ] **Step 6: Check** — `cd web && npx tsc -b && npm run lint && npm test -- --run`. Expected: clean.

- [ ] **Step 7: Look at it** — run the app (`run` skill), open a day with an absence, open a row's picker and check the strip at desktop and phone width.

- [ ] **Step 8: Commit** (`feat: Show day strip on candidates`).

---

## Part 2 — Parallel teachers

### Task 4: Class tokens and the `PARALLEL` status

**Files:**
- Create: `api/src/main/java/com/asendus/api/substitution/ClassTokens.java`
- Modify: `api/src/main/java/com/asendus/api/substitution/Availability.java`
- Test: `api/src/test/java/com/asendus/api/substitution/ClassTokensTest.java`

- [ ] **Step 1: Failing test**

```java
package com.asendus.api.substitution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassTokensTest {
    @Test void splitsOnSlashesAndIgnoresEmptyParts() {
        assertThat(ClassTokens.of("2.3/2.4/2.5/2.3V/")).containsExactlyInAnyOrder("2.3", "2.4", "2.5", "2.3v");
        assertThat(ClassTokens.of("")).isEmpty();
    }

    @Test void overlapIsExactAndCaseInsensitive() {
        assertThat(ClassTokens.overlap("7A/7B", "7b")).isTrue();
        assertThat(ClassTokens.overlap("2.3V", "2.3")).isFalse();
        assertThat(ClassTokens.overlap("/", "/")).isFalse();
        assertThat(ClassTokens.overlap("I klassid", "4R")).isFalse();
    }
}
```

- [ ] **Step 2: Run** `-Dtest=ClassTokensTest` → compilation failure.

- [ ] **Step 3: Implement**

```java
package com.asendus.api.substitution;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** A lesson's classes text ("2.3/2.4/" from TimetablePdfParser.joinClasses) as a set of class names. */
public final class ClassTokens {
    private ClassTokens() {}

    public static Set<String> of(String classes) {
        return Arrays.stream(classes.split("/")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /** Do the two lessons share a class? */
    public static boolean overlap(String a, String b) {
        Set<String> left = of(a);
        return of(b).stream().anyMatch(left::contains);
    }
}
```

`Availability.java`:
```java
package com.asendus.api.substitution;

/** PARALLEL: teaches the same class in that period (another group), so can take over half the group. */
public enum Availability { FREE, ASK_FIRST, PARALLEL, HIDDEN }
```
(`HIDDEN` stays last; it never reaches the client.)

- [ ] **Step 4: Run** `-Dtest='ClassTokensTest,EligibilityTest'` → PASS.

- [ ] **Step 5: Commit** (`feat: Add class token matching`).

---

### Task 5: `candidates` with a row → parallel teachers

**Files:**
- Modify: `api/src/main/java/com/asendus/api/substitution/DayDtos.java`
- Modify: `api/src/main/java/com/asendus/api/substitution/SubstitutionService.java`
- Modify: `api/src/main/java/com/asendus/api/substitution/DayController.java`
- Test: `api/src/test/java/com/asendus/api/substitution/DayControllerTest.java`

- [ ] **Step 1: Failing tests** (in `DayControllerTest`)

```java
    /**
     * A Friday pair (absent, parallel) whose lessons in the same period share a class, from the fixture PDF.
     * The absent teacher has exactly one lesson in that period, so their absence gives exactly one row.
     */
    record Pair(String absent, String parallel, int period) {}

    Pair parallelPair() {
        List<Pair> pairs = jdbc.query("""
                select ta.key a, tb.key b, la.period p from lesson la
                join teacher ta on ta.id = la.teacher_id join timetable_version v on v.id = ta.version_id and v.active
                join lesson lb on lb.weekday = la.weekday and lb.period = la.period and lb.teacher_id <> la.teacher_id
                join teacher tb on tb.id = lb.teacher_id and tb.version_id = v.id
                where la.weekday = 5
                  and (select count(*) from lesson x where x.teacher_id = la.teacher_id
                       and x.weekday = 5 and x.period = la.period) = 1
                  and array_remove(string_to_array(lower(la.classes), '/'), '') && array_remove(string_to_array(lower(lb.classes), '/'), '')
                order by ta.key, tb.key, la.period
                """, (rs, i) -> new Pair(rs.getString("a"), rs.getString("b"), rs.getInt("p")));
        assertThat(pairs).as("two teachers sharing a class in one Friday period").isNotEmpty();
        return pairs.getFirst();
    }

    ResultActions candidatesForRow(String date, int period, long rowId) throws Exception {
        return mockMvc.perform(get("/days/{d}/candidates", date).param("period", String.valueOf(period))
                .param("row", String.valueOf(rowId)).header("Authorization", bearer()));
    }

    @Test
    void aRowBringsInTeachersOfTheSameClassAtTheSameTime() throws Exception {
        Pair pair = parallelPair();
        String absence = absent(pair.absent(), pair.period(), pair.period());
        long rowId = id(absence, "$.rows[0].id");

        String body = candidatesForRow(FRI, pair.period(), rowId).andExpect(status().isOk())
                .andExpect(jsonPath(byKey(pair.parallel()) + ".status", contains("PARALLEL")))
                .andExpect(jsonPath(byKey(pair.parallel()) + ".parallelLesson", contains(not(emptyOrNullString()))))
                .andExpect(jsonPath(byKey(pair.absent())).isEmpty())
                .andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(body, "$[*].status");
        assertThat(statuses).isSortedAccordingTo(Comparator.comparing(Availability::valueOf));

        // Same subject as the row first among PARALLEL (subject compared the way the service does, from the DB)
        String subject = JsonPath.read(absence, "$.rows[0].subject");
        List<String> parallelKeys = JsonPath.read(body, "$[?(@.status=='PARALLEL')].key");
        List<Boolean> same = parallelKeys.stream().map(k -> jdbc.queryForObject("""
                select exists (select 1 from lesson l join teacher t on t.id = l.teacher_id
                join timetable_version v on v.id = t.version_id and v.active
                where t.key = ? and l.weekday = 5 and l.period = ? and l.subject = ?)""",
                Boolean.class, k, pair.period(), subject)).toList();
        assertThat(same).isSortedAccordingTo(Comparator.reverseOrder());

        // Without a row nothing changes
        candidates(pair.period()).andExpect(jsonPath(byKey(pair.parallel())).isEmpty());

        // Once they substitute in that period they drop out
        putSubstitution(rowId, "{\"substituteKeys\":[\"" + pair.parallel() + "\"],\"freeText\":\"\"}").andExpect(status().isOk());
        candidatesForRow(FRI, pair.period(), rowId).andExpect(status().isOk())
                .andExpect(jsonPath(byKey(pair.parallel())).isEmpty());
    }

    @Test
    void aRowMustBelongToTheDateAndPeriod() throws Exception {
        Pair pair = parallelPair();
        long rowId = id(absent(pair.absent(), pair.period(), pair.period()), "$.rows[0].id");
        int otherPeriod = pair.period() == 1 ? 2 : 1;
        candidatesForRow(FRI, otherPeriod, rowId).andExpect(status().isBadRequest());
        candidatesForRow("2026-09-11", pair.period(), rowId).andExpect(status().isBadRequest());
        candidatesForRow(FRI, pair.period(), 999_999).andExpect(status().isNotFound());
    }
```
(`not`/`emptyOrNullString` come from `org.hamcrest.Matchers.*`, already imported with a wildcard.)

- [ ] **Step 2: Run** `-Dtest='DayControllerTest#aRow*'` → FAIL (`row` ignored, no `PARALLEL`).

If `parallelPair()` finds nothing, the fixture has no such pair: stop and report — don't invent data.

- [ ] **Step 3: DTO**

```java
    /**
     * duties: labels of the candidate's break duties in the requested period. day: their 7 periods.
     * streak: periods in a row they'd work if they take this one. parallelLesson: for PARALLEL, their own lesson
     * in that period ("7A/7B · Sk", several joined with "; "); null otherwise.
     */
    public record CandidateDto(String key, String name, Availability status, List<String> subjects, int todayCount,
                               List<String> duties, List<DaySlot> day, int streak, String parallelLesson) {}
```

- [ ] **Step 4: Service** — replace `CANDIDATE_ORDER` and `candidates(...)`:

Remove the `CANDIDATE_ORDER` constant and add:
```java
    /** FREE, ASK_FIRST, PARALLEL; then the row's subject first; a duty in the period last; fewest today; name. */
    private static Comparator<CandidateDto> candidateOrder(Set<String> sameSubject) {
        return Comparator.comparing(CandidateDto::status)
                .thenComparing(c -> !sameSubject.contains(c.key()))
                .thenComparing(c -> !c.duties().isEmpty())
                .thenComparingInt(CandidateDto::todayCount)
                .thenComparing(CandidateDto::name, TeacherKey.DISPLAY_ORDER);
    }
```

New `candidates`:
```java
    /**
     * Who can cover period p on the date: every active-version teacher who teaches that weekday, except
     * placeholders, teachers absent in p and teachers already substituting in p. HIDDEN ones are left out.
     * With a row, teachers whose own lesson in p shares a class with it are PARALLEL.
     */
    @Transactional(readOnly = true)
    public List<CandidateDto> candidates(LocalDate date, int period, Long rowId) {
        int weekday = schoolWeekday(date);
        if (period < FIRST_PERIOD || period > LAST_PERIOD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tund peab olema vahemikus 1–7");
        }
        Substitution row = rowId == null ? null : rowOn(rowId, date, period);
        Map<Teacher, List<Lesson>> lessons = timetableService.lessonsOn(weekday);
        Set<String> placeholders = timetableService.placeholderKeys();
        List<Absence> absences = absencesOn(date);
        List<Substitution> rows = rowsOf(absences);

        Set<String> blocked = new HashSet<>(placeholders);
        blocked.addAll(absentIn(absences, period));
        blocked.addAll(substitutingIn(rows, period));
        Map<String, Integer> todayCounts = todayCounts(rows);
        Map<String, List<String>> subjects = timetableService.subjectsByTeacherKey();
        Map<String, List<Duty>> duties = dutyService.dutiesOn(weekday);
        Map<String, List<Substitution>> substituting = substitutingByKey(rows);

        List<CandidateDto> out = new ArrayList<>();
        Set<String> sameSubject = new HashSet<>();
        lessons.forEach((teacher, dayLessons) -> {
            String key = teacher.getTeacherKey();
            boolean isBlocked = blocked.contains(key);
            Availability status = Eligibility.of(periodsOf(dayLessons), period, isBlocked);
            List<Lesson> parallel = isBlocked ? List.of() : parallelLessons(dayLessons, row, period);
            if (status == Availability.HIDDEN && !parallel.isEmpty()) status = Availability.PARALLEL;
            if (status == Availability.HIDDEN) return;

            if (parallel.stream().anyMatch(l -> l.getSubject().equals(row.getSubject()))) sameSubject.add(key);
            List<String> dutyLabels = duties.getOrDefault(key, List.of()).stream()
                    .filter(d -> d.getPeriod() == period).map(DutyLabels::label).toList();
            List<DaySlot> day = DayStrip.of(dayLessons, substituting.getOrDefault(key, List.of()),
                    duties.getOrDefault(key, List.of()));
            String parallelLesson = parallel.isEmpty() ? null : parallel.stream()
                    .map(l -> DayStrip.label(l.getClasses(), l.getSubject())).collect(Collectors.joining("; "));
            out.add(new CandidateDto(key, TeacherKey.displayName(teacher.getName()), status,
                    subjects.getOrDefault(key, List.of()), todayCounts.getOrDefault(key, 0), dutyLabels,
                    day, DayStrip.streak(day, period), parallelLesson));
        });
        out.sort(candidateOrder(sameSubject));
        return out;
    }

    /** The row being filled; it must be on that date and in that period. */
    private Substitution rowOn(Long rowId, LocalDate date, int period) {
        Substitution row = substitutionRepository.findById(rowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asendust ei leitud"));
        Absence absence = absenceRepository.findById(row.getAbsenceId()).orElseThrow();
        if (row.getPeriod() != period || !absence.getAbsenceDate().equals(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rida ei ole selle päeva ja tunni oma");
        }
        return row;
    }

    /** Their lessons in the period that share a class with the row; none without a row or for a duty row. */
    private static List<Lesson> parallelLessons(List<Lesson> dayLessons, Substitution row, int period) {
        if (row == null || row.getDutyLabel() != null) return List.of();
        return dayLessons.stream()
                .filter(l -> l.getPeriod() == period && ClassTokens.overlap(l.getClasses(), row.getClasses()))
                .toList();
    }
```
Note the `return` inside `forEach` skips that teacher. `parallel` is non-empty only when `row != null`, so `row.getSubject()` is safe.

- [ ] **Step 5: Controller**

```java
    /** row: the substitution row being filled; adds teachers teaching the same class at the same time. */
    @GetMapping("/days/{date}/candidates")
    public List<CandidateDto> candidates(@PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date,
                                         @RequestParam int period,
                                         @RequestParam(required = false) Long row) {
        return substitutionService.candidates(date, period, row);
    }
```

- [ ] **Step 6: Run** `-Dtest='DayControllerTest,DutyDayTest,AbsenceRaceTest'` → all PASS (existing order tests still hold: without a row nobody is `PARALLEL` and `sameSubject` is empty).

- [ ] **Step 7: Commit** (`feat: Offer parallel teachers for a row`).

---

### Task 6: Parallel group in the picker (web)

**Files:**
- Modify: `web/src/api/types.ts`
- Modify: `web/src/pages/day/CandidatePicker.tsx`
- Modify: `web/src/help/guideSections.tsx` (`SubstituteHelp`)

- [ ] **Step 1: Types** — in `Candidate`: `status: 'FREE' | 'ASK_FIRST' | 'PARALLEL'` and `parallelLesson: string | null`.

- [ ] **Step 2: Query with the row**

```tsx
  const candidates = useQuery({
    queryKey: ['candidates', date, row.period, row.id],
    queryFn: () => api<Candidate[]>(`/days/${date}/candidates?period=${row.period}&row=${row.id}`),
  })
```

- [ ] **Step 3: Group** — next to `askFirst`: `const parallel = others.filter(c => c.status === 'PARALLEL')`, and after the "Küsi enne" group:
```tsx
          {parallel.length > 0 && (
            <Group
              title="Sama klass samal ajal"
              hint="grupp pooleks"
              dot="bg-teal-500"
              tip={<InfoTip label="Sama klass samal ajal">Neil on samal tunnil tund samale klassile (nt teine keelerühm). Vali üks või mitu, kui jagad puuduja rühma nende vahel.</InfoTip>}
            >
              {parallel.map(c => <CandidateButton key={c.key} candidate={c} period={row.period} onClick={() => toggle(c.key)} />)}
            </Group>
          )}
```

- [ ] **Step 4: Button tone and caption** — in `CandidateButton`:
```tsx
  const tone = {
    FREE: 'border-casual-green-outline border-b-casual-green bg-green-50 hover:bg-green-100',
    ASK_FIRST: 'border-casual-orange-outline border-b-casual-orange bg-orange-50 hover:bg-orange-100',
    PARALLEL: 'border-teal-300 border-b-teal-500 bg-teal-50 hover:bg-teal-100',
  }[c.status]
```
and replace the subjects caption block with:
```tsx
      {(c.parallelLesson || c.subjects.length > 0 || c.todayCount > 0) && (
        <span className="block text-xs text-text-caption">
          {c.parallelLesson ? `annab ${c.parallelLesson}` : c.subjects.map(subjectName).join(', ')}
          {(c.parallelLesson || c.subjects.length > 0) && c.todayCount > 0 && ' · '}
          {c.todayCount > 0 && <span className="font-bold text-text-primary">täna {c.todayCount} {c.todayCount === 1 ? 'asendus' : 'asendust'}</span>}
        </span>
      )}
```

- [ ] **Step 5: Guide** — in `SubstituteHelp`, add a third `Legend` in the grid:
```tsx
        <Legend dot="bg-teal-500" title="Sama klass samal ajal">
          Neil on samal tunnil endal tund samale klassile, näiteks teine keelerühm. Vali üks või mitu, kui jagad
          puuduja rühma nende vahel (grupp pooleks).
        </Legend>
```

- [ ] **Step 6: Check** — `cd web && npx tsc -b && npm run lint && npm test -- --run`.

- [ ] **Step 7: Look at it** — run the app, mark absent a teacher who has a group lesson, open the row and select two parallel teachers; the row shows "A / B".

- [ ] **Step 8: Commit** (`feat: Show parallel teachers in picker`).

---

## Part 3 — Renaming teachers

### Task 7: Name override storage and display names

**Files:**
- Create: `api/src/main/resources/db/migration/V4__teacher_name_override.sql`
- Create: `api/src/main/java/com/asendus/api/timetable/TeacherNameOverride.java`
- Create: `api/src/main/java/com/asendus/api/timetable/TeacherNameOverrideRepository.java`
- Modify: `api/src/main/java/com/asendus/api/timetable/TimetableService.java`
- Modify: `api/src/main/java/com/asendus/api/timetable/TimetableDtos.java`
- Modify: `api/src/main/java/com/asendus/api/substitution/SubstitutionService.java`
- Modify: every test `TRUNCATE ... placeholder_flag ...` (`DayControllerTest`, `DutyDayTest`, `AbsenceRaceTest`, `TimetableControllerTest`, `DutyControllerTest`): add `teacher_name_override`.
- Test: `api/src/test/java/com/asendus/api/timetable/TimetableControllerTest.java`

The endpoint comes in Task 8; this task tests through the repository.

- [ ] **Step 1: Migration**

```sql
-- A display name set by hand, by teacher key, so it carries over to later timetable versions.
CREATE TABLE teacher_name_override (
    teacher_key  TEXT PRIMARY KEY,
    display_name TEXT NOT NULL CHECK (length(trim(display_name)) > 0)
);
```

- [ ] **Step 2: Entity and repository** (same shape as `PlaceholderFlag`)

```java
package com.asendus.api.timetable;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teacher_name_override")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherNameOverride {

    @Id
    @Column(name = "teacher_key")
    private String teacherKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;
}
```
```java
package com.asendus.api.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherNameOverrideRepository extends JpaRepository<TeacherNameOverride, String> {
}
```

- [ ] **Step 3: Failing test** (add to `TimetableControllerTest`; also add `teacher_name_override` to its `TRUNCATE`)

```java
    @Autowired TeacherNameOverrideRepository nameOverrides;

    @Test
    void anOverriddenNameIsShownEverywhere() throws Exception {
        activate(upload("opetajad-b.pdf"));
        nameOverrides.save(new TeacherNameOverride("pärnpuu taimi", "Taimi Testnimi"));

        mockMvc.perform(get("/timetables/active").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachers[?(@.key=='pärnpuu taimi')].displayName").value("Taimi Testnimi"));
        mockMvc.perform(get("/timetables/active/teachers/{k}", "pärnpuu taimi").header("Authorization", bearer()))
                .andExpect(jsonPath("$.displayName").value("Taimi Testnimi"))
                .andExpect(jsonPath("$.name").value("Pärnpuu Taimi"))
                .andExpect(jsonPath("$.renamed").value(true));
        mockMvc.perform(get("/days/{d}/candidates", "2026-09-04").param("period", "5").header("Authorization", bearer()))
                .andExpect(jsonPath("$[?(@.key=='pärnpuu taimi')].name").value("Taimi Testnimi"));
        mockMvc.perform(post("/days/{d}/absences", "2026-09-04").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teacherKey\":\"pärnpuu taimi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherName").value("Taimi Testnimi"));
    }
```
(Use the existing `upload`/`activate` helpers; `.value(...)` on a one-element filter result works, as in the placeholder test.) Add the `MediaType` import if missing.

- [ ] **Step 4: Run** `-Dtest=TimetableControllerTest#anOverriddenNameIsShownEverywhere` → FAIL (PDF name shown).

- [ ] **Step 5: `TimetableService`** — inject `private final TeacherNameOverrideRepository nameOverrideRepository;` and add:

```java
    /** key → display name set by hand (across versions). */
    @Transactional(readOnly = true)
    public Map<String, String> nameOverrides() {
        return nameOverrideRepository.findAll().stream()
                .collect(Collectors.toMap(TeacherNameOverride::getTeacherKey, TeacherNameOverride::getDisplayName));
    }

    /** The name shown everywhere: set by hand, else the PDF's "Firstname Surname". */
    public static String displayName(Teacher t, Map<String, String> overrides) {
        String set = overrides.get(t.getTeacherKey());
        return set != null ? set : TeacherKey.displayName(t.getName());
    }

    @Transactional(readOnly = true)
    public String displayName(Teacher t) {
        return nameOverrideRepository.findById(t.getTeacherKey()).map(TeacherNameOverride::getDisplayName)
                .orElseGet(() -> TeacherKey.displayName(t.getName()));
    }
```
In `active()`: load `Map<String, String> names = nameOverrides();` and use `displayName(t, names)` for `TeacherSummaryDto.displayName`.
In `grid()`:
```java
        Optional<TeacherNameOverride> set = nameOverrideRepository.findById(t.getTeacherKey());
        return new TeacherGridDto(t.getTeacherKey(), t.getName(),
                set.map(TeacherNameOverride::getDisplayName).orElseGet(() -> TeacherKey.displayName(t.getName())),
                set.isPresent(), placeholderFlagRepository.existsById(t.getTeacherKey()), lessons);
```
Leave `difference()` (upload preview) on PDF names.

`TimetableDtos`:
```java
    /** name: as printed in the PDF; displayName: set by hand or from the PDF; renamed: set by hand. */
    public record TeacherGridDto(String key, String name, String displayName, boolean renamed, boolean placeholder,
                                 List<LessonDto> lessons) {}
```

- [ ] **Step 6: `SubstitutionService`**
- `createAbsence`: `.teacherName(timetableService.displayName(teacher))`.
- `nameFor`: `.map(timetableService::displayName)`.
- `candidates`: `Map<String, String> names = timetableService.nameOverrides();` before the loop; `TimetableService.displayName(teacher, names)` in the `CandidateDto`.

- [ ] **Step 7: Run** `-Dtest='TimetableControllerTest,DayControllerTest'` → PASS.

- [ ] **Step 8: Commit** (`feat: Store teacher display names`).

---

### Task 8: Rename endpoint and duty re-matching

**Files:**
- Create: `api/src/main/java/com/asendus/api/timetable/TeacherRenameService.java`
- Modify: `api/src/main/java/com/asendus/api/timetable/TimetableService.java` (`rename`)
- Modify: `api/src/main/java/com/asendus/api/timetable/TimetableController.java`
- Modify: `api/src/main/java/com/asendus/api/timetable/TimetableDtos.java` (`RenameRequest`)
- Modify: `api/src/main/java/com/asendus/api/duty/DutyService.java`
- Test: `TimetableControllerTest`, `DutyControllerTest`

- [ ] **Step 1: Failing tests**

`TimetableControllerTest`:
```java
    ResultActions rename(String key, String name) throws Exception {
        return mockMvc.perform(put("/teachers/{key}/name", key).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + name + "\"}"));
    }

    @Test
    void renameTrimsRestoresAndSurvivesANewVersion() throws Exception {
        activate(upload("opetajad-b.pdf"));
        rename("pärnpuu taimi", "  Taimi   Testnimi ").andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Taimi Testnimi"))
                .andExpect(jsonPath("$.renamed").value(true));

        activate(upload("opetajad-a.pdf"));
        mockMvc.perform(get("/timetables/active/teachers/{k}", "pärnpuu taimi").header("Authorization", bearer()))
                .andExpect(jsonPath("$.displayName").value("Taimi Testnimi"));

        rename("pärnpuu taimi", "  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Taimi Pärnpuu"))
                .andExpect(jsonPath("$.renamed").value(false));
    }

    @Test
    void renameRejectsUnknownTeachersAndLongNames() throws Exception {
        activate(upload("opetajad-b.pdf"));
        rename("pole olemas", "X").andExpect(status().isNotFound());
        rename("pärnpuu taimi", "x".repeat(101)).andExpect(status().isBadRequest());
    }
```
(`opetajad-a.pdf` is byte-identical to `-b`, so the teacher is there; this file needs `import org.springframework.test.web.servlet.ResultActions;`.)

`DutyControllerTest` (add `teacher_name_override` to its `TRUNCATE`):
```java
    ResultActions rename(String key, String name) throws Exception {
        return mockMvc.perform(put("/teachers/{key}/name", key).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + name + "\"}"));
    }

    String keyWithSurname(String surname) {
        return jdbc.queryForObject("""
                select t.key from teacher t join timetable_version v on v.id = t.version_id and v.active
                where split_part(t.name, ' ', 1) = ?""", String.class, surname);
    }

    @Test void renamingMatchesALeftOverDutyWithoutReupload() throws Exception {
        assertThat(JsonPath.<List<String>>read(uploadJson(), "$.unresolved[*].rawName")).contains(NOBODY);
        String other = jdbc.queryForList("""
                select split_part(t.name, ' ', 1) s from teacher t
                join timetable_version v on v.id = t.version_id and v.active
                group by s having count(*) = 1 order by s""", String.class).get(2);
        rename(keyWithSurname(other), "Mari " + NOBODY).andExpect(status().isOk());

        String json = mockMvc.perform(get("/duties").header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(json, "$.unresolved[*].rawName")).doesNotContain(NOBODY);
        assertThat((Integer) JsonPath.read(json, "$.dutyCount")).isEqualTo(2);
    }

    @Test void aRenameKeepingTheSurnameIsNotAmbiguous() throws Exception {
        rename(keyWithSurname(unique), "Uus " + unique).andExpect(status().isOk());
        String json = uploadJson();
        assertThat(JsonPath.<List<String>>read(json, "$.unresolved[*].rawName")).doesNotContain(unique);
    }
```
Imports as needed (`put`, `MediaType`, `ResultActions`).

- [ ] **Step 2: Run** `-Dtest='TimetableControllerTest#rename*,DutyControllerTest#*Rename*,DutyControllerTest#renaming*'` → FAIL (404/405, no endpoint).

- [ ] **Step 3: `TimetableService.rename`**

```java
    /** Sets the teacher's display name; blank goes back to the PDF name. Stored by key like placeholder flags. */
    @Transactional
    public TeacherGridDto rename(String key, String displayName) {
        Teacher t = activeTeacher(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Õpetajat ei leitud"));
        String name = Normalizer.normalize(displayName, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            nameOverrideRepository.deleteById(t.getTeacherKey());
        } else {
            nameOverrideRepository.save(new TeacherNameOverride(t.getTeacherKey(), name));
        }
        nameOverrideRepository.flush();
        return grid(t.getTeacherKey());
    }
```
(import `java.text.Normalizer`).

`TimetableDtos`: `public record RenameRequest(@NotNull @Size(max = 100) String displayName) {}` (jakarta.validation imports).

- [ ] **Step 4: `DutyService`** — display names, deduplicated surname groups, `rematch`

Replace the `bySurname` building in `upload` and the static `toDuty`:
```java
        Map<String, String> names = timetableService.nameOverrides();
        Map<String, List<Teacher>> bySurname = bySurname(teachers, names);
        ...
        dutyRepository.saveAll(parsed.duties().stream().map(p -> toDuty(v.getId(), p, bySurname, names)).toList());
```
```java
    private static Duty toDuty(Long versionId, ParsedDuty p, Map<String, List<Teacher>> bySurname, Map<String, String> names) {
        List<Teacher> matches = bySurname.getOrDefault(fold(p.surname()), List.of());
        Duty.DutyBuilder b = Duty.builder().versionId(versionId)
                .weekday((short) p.weekday()).period((short) p.period()).kind(p.kind())
                .starts(p.starts()).ends(p.ends()).classGroup(p.classGroup()).position(p.position())
                .rawName(p.surname());
        if (matches.size() == 1) {
            Teacher t = matches.get(0);
            b.teacherKey(t.getTeacherKey()).teacherName(TimetableService.displayName(t, names));
        } else {
            b.candidateKeys(new ArrayList<>(matches.stream().map(Teacher::getTeacherKey).toList()));
        }
        return b.build();
    }

    /**
     * Teachers by folded surname: the PDF's, and for a renamed teacher also the new name's last word.
     * A teacher is in a group once even if both surnames fold the same.
     */
    private static Map<String, List<Teacher>> bySurname(List<Teacher> teachers, Map<String, String> names) {
        Map<String, Map<String, Teacher>> groups = new HashMap<>();
        for (Teacher t : teachers) {
            groups.computeIfAbsent(fold(surnameOf(t.getName())), k -> new LinkedHashMap<>()).put(t.getTeacherKey(), t);
            String renamed = names.get(t.getTeacherKey());
            if (renamed != null) {
                groups.computeIfAbsent(fold(lastWord(renamed)), k -> new LinkedHashMap<>()).put(t.getTeacherKey(), t);
            }
        }
        Map<String, List<Teacher>> out = new HashMap<>();
        groups.forEach((s, byKey) -> out.put(s, List.copyOf(byKey.values())));
        return out;
    }

    /** "Firstname Surname" → "Surname". */
    private static String lastWord(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1];
    }
```
`resolve`: `d.setTeacherName(timetableService.displayName(t));`
`summary`: build `names` with `TimetableService.displayName(t, overrides)` where `Map<String, String> overrides = timetableService.nameOverrides();`.

Add:
```java
    /**
     * After a rename: duties left unmatched at upload are matched again by their surname, and the teacher's
     * matched duties take the new name. Nothing to do without a duty upload.
     */
    @Transactional
    public void rematch(String teacherKey) {
        Optional<DutyVersion> v = versionRepository.findFirstByOrderByIdDesc();
        if (v.isEmpty()) return;
        List<Teacher> teachers = timetableService.activeTeachers();
        Map<String, String> names = timetableService.nameOverrides();
        Map<String, List<Teacher>> bySurname = bySurname(teachers, names);
        List<Duty> changed = new ArrayList<>();
        for (Duty d : dutyRepository.findByVersionIdOrderByWeekdayAscPeriodAscIdAsc(v.get().getId())) {
            if (d.getTeacherKey() == null) {
                List<Teacher> matches = bySurname.getOrDefault(fold(d.getRawName()), List.of());
                if (matches.size() == 1) {
                    d.setTeacherKey(matches.get(0).getTeacherKey());
                    d.setTeacherName(TimetableService.displayName(matches.get(0), names));
                    d.setCandidateKeys(new ArrayList<>());
                } else {
                    d.setCandidateKeys(new ArrayList<>(matches.stream().map(Teacher::getTeacherKey).toList()));
                }
                changed.add(d);
            } else if (d.getTeacherKey().equals(teacherKey)) {
                teachers.stream().filter(t -> t.getTeacherKey().equals(teacherKey)).findFirst()
                        .ifPresent(t -> d.setTeacherName(TimetableService.displayName(t, names)));
                changed.add(d);
            }
        }
        dutyRepository.saveAll(changed);
    }
```

- [ ] **Step 5: `TeacherRenameService`** (avoids a `TimetableService` ↔ `DutyService` cycle)

```java
package com.asendus.api.timetable;

import com.asendus.api.duty.DutyService;
import com.asendus.api.timetable.TimetableDtos.TeacherGridDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renames a teacher and lets break duties pick up the new surname, in one transaction. */
@Service
@RequiredArgsConstructor
public class TeacherRenameService {
    private final TimetableService timetableService;
    private final DutyService dutyService;

    @Transactional
    public TeacherGridDto rename(String key, String displayName) {
        TeacherGridDto grid = timetableService.rename(key, displayName);
        dutyService.rematch(grid.key());
        return grid;
    }
}
```

- [ ] **Step 6: Controller** — inject `TeacherRenameService teacherRenameService` and add:
```java
    /** A blank name goes back to the PDF's. */
    @PutMapping("/teachers/{key}/name")
    public TeacherGridDto rename(@PathVariable String key, @Valid @RequestBody RenameRequest request) {
        return teacherRenameService.rename(key, request.displayName());
    }
```
(import `jakarta.validation.Valid` and `RenameRequest`).

- [ ] **Step 7: Run the full API suite** — `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q verify` → all PASS.

- [ ] **Step 8: Commit** (`feat: Rename teachers and rematch duties`).

---

### Task 9: Rename in the timetable page (web)

**Files:**
- Modify: `web/src/api/types.ts` (`TeacherGrid.renamed`)
- Modify: `web/src/pages/timetable/TeacherGrid.tsx`
- Modify: `web/src/help/guideSections.tsx` (`TimetableHelp`)

- [ ] **Step 1: Types** — `TeacherGrid` gets `renamed: boolean`.

- [ ] **Step 2: Name editor** — in `TeacherGrid.tsx`, replace `<h2 className="mb-4 text-lg font-bold">{data.displayName}</h2>` with `<TeacherName grid={data} />` and add:

```tsx
function TeacherName({ grid }: { grid: Grid }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  // Removing the focused input fires blur (Chrome); after Escape that blur must not save the typed text.
  const cancelled = useRef(false)
  const rename = useMutation({
    mutationFn: (displayName: string) =>
      api<Grid>(`/teachers/${encodeURIComponent(grid.key)}/name`, { method: 'PUT', body: JSON.stringify({ displayName }) }),
    onSuccess: saved => {
      qc.setQueryData(['timetable', 'teacher', grid.key], saved)
      setEditing(false)
    },
    onSettled: () => {
      for (const key of ['timetable', 'candidates', 'day', 'duties']) qc.invalidateQueries({ queryKey: [key] })
    },
  })
  const pdfName = grid.name.includes(' ') ? `${grid.name.slice(grid.name.indexOf(' ') + 1)} ${grid.name.split(' ')[0]}` : grid.name
  const save = (value: string) => { if (value.trim() !== grid.displayName) rename.mutate(value); else setEditing(false) }

  return (
    <div className="mb-4">
      {editing ? (
        <input
          autoFocus
          defaultValue={grid.displayName}
          maxLength={100}
          aria-label="Õpetaja nimi"
          className="field w-full max-w-sm text-lg font-bold"
          onBlur={e => {
            if (cancelled.current) { cancelled.current = false; setEditing(false); return }
            save(e.target.value)
          }}
          onKeyDown={e => {
            if (e.key === 'Enter') e.currentTarget.blur()
            if (e.key === 'Escape') { cancelled.current = true; e.currentTarget.blur() }
          }}
        />
      ) : (
        <h2 className="flex items-center gap-2 text-lg font-bold">
          {grid.displayName}
          <button onClick={() => setEditing(true)} aria-label="Muuda nime" title="Muuda nime"
            className="min-h-11 min-w-11 rounded-xl text-text-caption hover:bg-slate-100">✎</button>
        </h2>
      )}
      {grid.renamed && (
        <p className="text-sm text-text-caption">
          PDF-is: {pdfName} ·{' '}
          <button onClick={() => rename.mutate('')} className="font-semibold text-casual-sky-blue-darker hover:underline">Taasta</button>
        </p>
      )}
      {rename.isError && <p className="text-sm font-semibold text-casual-red-darker">{rename.error.message}</p>}
    </div>
  )
}
```
Imports: `useRef, useState` from react; `useMutation, useQueryClient` from TanStack Query. Verify in the browser that Escape leaves the name unchanged.

The `pdfName` swap mirrors `TeacherKey.displayName` ("Surname Firstname(s)" → "Firstname(s) Surname").

- [ ] **Step 3: Guide** — in `TimetableHelp`, after the existing `Tip`:
```tsx
      <Tip>
        Vali nimekirjast õpetaja ja vajuta tema nime kõrval <b>✎</b>, kui nimi on PDF-is vale või kohatäitja asemel on
        nüüd päris õpetaja. Uus nimi jääb alles ka järgmise tunniplaani juures. <b>Taasta</b> paneb PDF-i nime tagasi.
      </Tip>
```

- [ ] **Step 4: Check** — `cd web && npx tsc -b && npm run lint && npm test -- --run`.

- [ ] **Step 5: Look at it** — run the app; rename a teacher, see the list, the day page candidate and the duty card update; Taasta brings the PDF name back; Escape cancels.

- [ ] **Step 6: Commit** (`feat: Edit teacher names on timetable page`).

---

### Task 10: Final verification

- [ ] `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q verify` → PASS.
- [ ] `cd web && npx tsc -b && npm run lint && npm test -- --run && npm run build` → clean.
- [ ] Add a line per feature to `web/CHANGELOG.txt` and `api/CHANGELOG.txt` in their existing style; commit (`docs: Update changelogs for candidate features`, `Changelog: Documentation`).
