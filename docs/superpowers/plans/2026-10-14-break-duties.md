# Break Duties (ÕVT / SVT) Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers-extended-cc:subagent-driven-development (if subagents available) or superpowers-extended-cc:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aili uploads the break-duty Excel; candidates with a duty in the period get a violet badge and sort last within their group, and an absent teacher's duties become rows to cover.

**Architecture:** New package `com.asendus.api.duty` (Apache POI parser → `DutyService` → `DutyController`), linked to teachers by `teacher_key` like placeholder flags. `SubstitutionService` asks `DutyService` for the weekday's duties when listing candidates and creating absences. Web gets a second upload card on Tunniplaan with a "Vali õpetaja" list for unmatched names, a badge on candidate tiles and a duty accent on day rows.

**Tech Stack:** Spring Boot 4 / Java 25 / Postgres / Flyway, Apache POI 5.4.1 (`poi-ooxml`), React 19 + TanStack Query, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-14-break-duties-design.md` — read it first.

**Conventions (from this repo):**
- Run API tests: `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q test -Dtest=<Class>` (Docker must be running for integration tests). Full: `./mvnw verify`.
- Run web tests: `cd web && npm test -- --run`; types: `npx tsc -b`.
- All user-facing error/warning text is Estonian. Errors are `ResponseStatusException`.
- Commit with the `commit` skill (subject `type: Capitalised three+ words`, last body line `Changelog: Feature`). No Co-authored-by.
- The repo is **public**: never commit the school's Excel, nor any real name or phone number from it. Tests build
  their Excel in code (`TestDutySheet`) with made-up surnames; integration tests take surnames from the timetable PDF
  fixture that is already in the repo, at runtime, so no new real name is written into code.
- Duty colour is **violet** (`bg-violet-500`, `border-l-violet-500`): orange already means "Küsi enne".

---

### Task 1: POI dependency, test sheet builder, migration and entities

**Files:**
- Create: `api/src/test/java/com/asendus/api/TestDutySheet.java`
- Modify: `api/pom.xml` (after the pdfbox dependency)
- Create: `api/src/main/resources/db/migration/V3__duties.sql`
- Create: `api/src/main/java/com/asendus/api/duty/{DutyVersion,Duty,DutyVersionRepository,DutyRepository}.java`
- Modify: `api/src/main/java/com/asendus/api/substitution/Substitution.java`
- Modify: `api/src/test/java/com/asendus/api/timetable/SchemaTest.java`
- Modify: every test `TRUNCATE` (`DayControllerTest`, `AbsenceRaceTest`, `TimetableControllerTest`): add `duty_version` so duties from one test class never leak into another.

- [ ] **Step 1: Add POI** (Step 2 below needs it on the test classpath too)

```xml
		<dependency>
			<groupId>org.apache.poi</groupId>
			<artifactId>poi-ooxml</artifactId>
			<version>5.4.1</version>
		</dependency>
```

- [ ] **Step 2: `TestDutySheet`** — builds an xlsx in memory with the real sheet's layout. Times in D are real
  Excel time cells (format `h:mm`) and positions are numeric cells, like in the school's file, so the parser's
  `DataFormatter` path is exercised.

```java
package com.asendus.api;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalTime;

/** A break-duty Excel like the school's "alates …" sheet (columns A–I), built in memory with made-up names. */
public final class TestDutySheet {
    private final Workbook wb = new XSSFWorkbook();
    private final Sheet sheet = wb.createSheet("alates 7.09");
    private final CellStyle timeStyle = wb.createCellStyle();
    private int next = 0;

    public TestDutySheet() {
        timeStyle.setDataFormat(wb.createDataFormat().getFormat("h:mm"));
        cells(sheet.createRow(next++), "", "SÖÖGI-JA ÕUEVAHETUNNID");
        next++; // empty row, like the real sheet
    }

    /** A day header: A = E/T/K/N/R, C/D/E = ÕVT/SVT/ÕVT. */
    public TestDutySheet day(String letter) {
        cells(sheet.createRow(next++), letter, "", "ÕVT", "SVT", "ÕVT", "1.1 ja 1.2 klassid", "pos", "1.3 ja 1.4 klassid", "pos");
        return this;
    }

    /**
     * A class-group row. c = ÕVT time, canteen = SVT start (null for none), e = ÕVT range or note,
     * f/g and h/i = names and position (null position for none).
     */
    public TestDutySheet group(String b, String c, LocalTime canteen, String e,
                               String f, Integer g, String h, Integer i) {
        Row row = sheet.createRow(next++);
        cells(row, "", b, c);
        if (canteen != null) {
            Cell d = row.createCell(3);
            d.setCellValue(canteen.toSecondOfDay() / 86400.0);
            d.setCellStyle(timeStyle);
        }
        row.createCell(4).setCellValue(e);
        row.createCell(5).setCellValue(f);
        if (g != null) row.createCell(6).setCellValue(g);
        row.createCell(7).setCellValue(h);
        if (i != null) row.createCell(8).setCellValue(i);
        row.createCell(14).setCellValue("1.1 217 märkus"); // side notes far right are ignored
        return this;
    }

    /** An empty row ends a day block. */
    public TestDutySheet blank() {
        next++;
        return this;
    }

    public byte[] bytes() {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void cells(Row row, String... values) {
        for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
    }
}
```

- [ ] **Step 3: Migration `V3__duties.sql`**

```sql
-- Only the latest upload is kept; deleting it removes its duties.
CREATE TABLE duty_version (
    id          BIGSERIAL PRIMARY KEY,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by BIGINT      NOT NULL REFERENCES app_user (id),
    file_name   TEXT        NOT NULL,
    warnings    TEXT[]      NOT NULL DEFAULT '{}'
);

-- teacher_key is null until the surname is matched (several or no teachers with it).
CREATE TABLE duty (
    id             BIGSERIAL PRIMARY KEY,
    version_id     BIGINT   NOT NULL REFERENCES duty_version (id) ON DELETE CASCADE,
    weekday        SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 5),
    period         SMALLINT NOT NULL CHECK (period BETWEEN 1 AND 7),
    kind           TEXT     NOT NULL CHECK (kind IN ('OVT', 'SVT')),
    starts         TIME     NOT NULL,
    ends           TIME     NOT NULL,
    class_group    TEXT     NOT NULL,
    position       TEXT     NOT NULL DEFAULT '',
    raw_name       TEXT     NOT NULL,
    teacher_key    TEXT,
    teacher_name   TEXT,
    candidate_keys TEXT[]   NOT NULL DEFAULT '{}'
);
CREATE INDEX duty_version_weekday ON duty (version_id, weekday);

-- Set on rows that cover an absent teacher's break duty; null for lessons.
ALTER TABLE substitution ADD COLUMN duty_label TEXT;
```

- [ ] **Step 4: Entities and repositories** (match `TimetableVersion` / `Substitution` style: Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`)

`DutyVersion`: `id`, `@CreationTimestamp uploadedAt` (`Instant`, `updatable = false`), `uploadedBy` (`Long`), `fileName`, `warnings` (`List<String>`, `@JdbcTypeCode(SqlTypes.ARRAY)`, `columnDefinition = "text[]"`).

`Duty`: `id`, `versionId` (`version_id`), `short weekday`, `short period`, `String kind`, `LocalTime starts`, `LocalTime ends`, `classGroup` (`class_group`), `@Builder.Default String position = ""`, `rawName` (`raw_name`), `teacherKey` (`teacher_key`, nullable), `teacherName` (`teacher_name`, nullable), `@Builder.Default List<String> candidateKeys = new ArrayList<>()` (array column like `Substitution.substituteKeys`).

```java
public interface DutyVersionRepository extends JpaRepository<DutyVersion, Long> {
    Optional<DutyVersion> findFirstByOrderByIdDesc();
}

public interface DutyRepository extends JpaRepository<Duty, Long> {
    List<Duty> findByVersionIdOrderByWeekdayAscPeriodAscIdAsc(Long versionId);

    List<Duty> findByVersionIdAndWeekdayAndTeacherKeyNotNull(Long versionId, short weekday);
}
```

`Substitution`: add
```java
    /** e.g. "ÕVT 10:45–11:15 · pos 4" when the row covers a break duty; null for lessons. */
    @Column(name = "duty_label")
    private String dutyLabel;
```

- [ ] **Step 5: SchemaTest** — add a test that saves a `DutyVersion` (uploadedBy = reuse the user the existing tests save, or one with a new unique `googleSub`: `app_user` is never truncated) and an unresolved `Duty` with `candidateKeys = List.of("a", "b")` and `starts = LocalTime.of(10, 45)`, reloads it, and asserts the array, the time and the null `teacherKey`; and that a `Substitution` with `dutyLabel("ÕVT 10:45–11:15")` round-trips.

- [ ] **Step 6: Add `duty_version` to the three TRUNCATE statements**, e.g. `TRUNCATE timetable_version, placeholder_flag, absence, duty_version CASCADE`.

- [ ] **Step 7: Run** `./mvnw -q test -Dtest=SchemaTest` → PASS. Then the whole suite → 97+ tests PASS.

- [ ] **Step 8: Commit** — `feat: Add break duty tables`. `git ls-files | grep -i xlsx` must print nothing.

---

### Task 2: PeriodTimes

**Files:**
- Create: `api/src/main/java/com/asendus/api/duty/PeriodTimes.java`
- Test: `api/src/test/java/com/asendus/api/duty/PeriodTimesTest.java`

- [ ] **Step 1: Failing test**

```java
class PeriodTimesTest {
    static int p(String start, String end) {
        return PeriodTimes.periodOf(PeriodTimes.parse(start), PeriodTimes.parse(end));
    }

    @Test void outdoorBreaksFallInTheirLessonSlot() {
        assertThat(p("10:45", "11:15")).isEqualTo(3);
        assertThat(p("11:05", "11:30")).isEqualTo(3);
        assertThat(p("10:25", "11:05")).isEqualTo(3);  // touching 2nd period's end doesn't count
        assertThat(p("12:55", "13:20")).isEqualTo(5);
        assertThat(p("12:35", "13:10")).isEqualTo(5);
    }

    @Test void canteenDutyGoesToThePeriodItOverlapsMost() {
        assertThat(p("12:10", "12:55")).isEqualTo(5);   // 5 min of 4th, 30 min of 5th
    }

    @Test void tieGoesToTheEarlierPeriod() {
        assertThat(p("11:10", "11:40")).isEqualTo(3);   // 10 min each
    }

    @Test void outsideTheDayIsZero() {
        assertThat(p("15:10", "15:40")).isZero();
        assertThat(p("12:15", "12:25")).isZero();       // exactly the break between 4th and 5th
    }
}
```

- [ ] **Step 2: Run** → FAIL (class missing).

- [ ] **Step 3: Implement**

```java
package com.asendus.api.duty;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** The school's bell schedule, as printed in the aSc timetable PDF. */
public final class PeriodTimes {
    private PeriodTimes() {}

    private record Slot(LocalTime start, LocalTime end) {}

    private static final List<Slot> SLOTS = List.of(
            slot("8:45", "9:30"), slot("9:40", "10:25"), slot("10:35", "11:20"), slot("11:30", "12:15"),
            slot("12:25", "13:10"), slot("13:20", "14:05"), slot("14:15", "15:00"));

    /**
     * The period a break duty belongs to: the one it overlaps most (ties → the earlier one), or 0 if none.
     * The break fills the teacher's empty slot in the timetable, so one period is enough.
     */
    public static int periodOf(LocalTime start, LocalTime end) {
        int best = 0;
        long bestMinutes = 0;
        for (int i = 0; i < SLOTS.size(); i++) {
            Slot s = SLOTS.get(i);
            LocalTime from = start.isAfter(s.start()) ? start : s.start();
            LocalTime to = end.isBefore(s.end()) ? end : s.end();
            long minutes = Duration.between(from, to).toMinutes();
            if (minutes > bestMinutes) {
                best = i + 1;
                bestMinutes = minutes;
            }
        }
        return best;
    }

    /** "8:45" → 08:45. */
    static LocalTime parse(String hm) {
        String[] p = hm.split(":");
        return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    private static Slot slot(String start, String end) {
        return new Slot(parse(start), parse(end));
    }
}
```

- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: Add bell schedule for duties`.

---

### Task 3: DutyNames (cell → surnames) and DutyLabels

**Files:**
- Create: `api/src/main/java/com/asendus/api/duty/DutyNames.java`, `DutyLabels.java`
- Test: `api/src/test/java/com/asendus/api/duty/DutyNamesTest.java`

- [ ] **Step 1: Failing test** (patterns from the real sheet, made-up names)

```java
class DutyNamesTest {
    @Test void plainList() {
        assertThat(DutyNames.surnames("Kask, Tamm")).containsExactly("Kask", "Tamm");
    }
    @Test void canteenPrefix() {
        assertThat(DutyNames.isCanteen("sööklas: Mänd, Saar")).isTrue();
        assertThat(DutyNames.surnames("sööklas: Mänd, Saar")).containsExactly("Mänd", "Saar");
        assertThat(DutyNames.isCanteen("Kask")).isFalse();
    }
    @Test void cutsAtDoubleSlash() {
        assertThat(DutyNames.surnames("Lepp // 4.s klj tund (315)")).containsExactly("Lepp");
        assertThat(DutyNames.surnames("sööklas: Lepp, Pihlakas // 6.S ÕVT (12:35 -13:10) - pos 4 (Kask/Kuusk)"))
                .containsExactly("Lepp", "Pihlakas");
    }
    @Test void dropsParentheses() {
        assertThat(DutyNames.surnames("Kuusk (õvt +6.S)")).containsExactly("Kuusk");
        assertThat(DutyNames.surnames("Reimann ")).containsExactly("Reimann");
    }
    @Test void takesWhatFollowsDash() {
        assertThat(DutyNames.surnames("4.S & 6.R ÕVT - Vaher")).containsExactly("Vaher");
    }
    @Test void splitsOnAmpersandAndSlash() {
        assertThat(DutyNames.surnames("Loo & Kiik/Utt")).containsExactly("Loo", "Kiik", "Utt");
    }
    @Test void hiddenDutyIsDetected() {
        assertThat(DutyNames.hidesDuty("sööklas: Lepp, Pihlakas // 6.S ÕVT (12:35 -13:10) - pos 4 (Kask/Kuusk)")).isTrue();
        assertThat(DutyNames.hidesDuty("Kuusk (õvt +6.S)")).isTrue();
        assertThat(DutyNames.hidesDuty("Lepp // 4.s klj tund (315)")).isFalse();
        assertThat(DutyNames.hidesDuty("Kask, Tamm")).isFalse();
    }
    @Test void labels() {
        Duty d = Duty.builder().kind("OVT").starts(LocalTime.of(10, 45)).ends(LocalTime.of(11, 15)).position("4").build();
        assertThat(DutyLabels.label(d)).isEqualTo("ÕVT 10:45–11:15");
        assertThat(DutyLabels.labelWithPosition(d)).isEqualTo("ÕVT 10:45–11:15 · pos 4");
        d.setKind("SVT"); d.setPosition(""); d.setStarts(LocalTime.of(9, 5)); d.setEnds(LocalTime.of(9, 50));
        assertThat(DutyLabels.labelWithPosition(d)).isEqualTo("SVT 9:05–9:50");
    }
}
```

- [ ] **Step 2: Run** → FAIL.

- [ ] **Step 3: Implement**

```java
package com.asendus.api.duty;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the surnames out of a duty cell such as "sööklas: Lepp, Pihlakas // 6.S ÕVT (12:35 -13:10)". */
final class DutyNames {
    private DutyNames() {}

    /** "10:45-11:15", also with spaces or an en dash around the dash ("12:55- 13:20"). */
    static final Pattern RANGE = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[-–]\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final String CANTEEN = "sööklas:";

    static boolean isCanteen(String cell) {
        return lower(cell.trim()).startsWith(CANTEEN);
    }

    /** Strip "sööklas:", cut at "//", drop "(…)", keep what follows " - ", split on , & / */
    static List<String> surnames(String cell) {
        String t = cell.trim();
        if (isCanteen(t)) t = t.substring(CANTEEN.length());
        int cut = t.indexOf("//");
        if (cut >= 0) t = t.substring(0, cut);
        t = PARENS.matcher(t).replaceAll(" ");
        int dash = t.lastIndexOf(" - ");
        if (dash >= 0) t = t.substring(dash + 3);
        return Arrays.stream(t.split("[,&/]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** True when the text surnames() throws away mentions another duty (a time range or "õvt"). */
    static boolean hidesDuty(String cell) {
        int cut = cell.indexOf("//");
        String head = cut >= 0 ? cell.substring(0, cut) : cell;
        StringBuilder dropped = new StringBuilder(cut >= 0 ? cell.substring(cut) : "");
        Matcher m = PARENS.matcher(head);
        while (m.find()) dropped.append(' ').append(m.group());
        String d = lower(dropped.toString());
        return d.contains("õvt") || RANGE.matcher(d).find();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
```

```java
package com.asendus.api.duty;

import java.time.LocalTime;

/** How a duty is shown: "ÕVT 10:45–11:15", with " · pos 4" on day rows. */
public final class DutyLabels {
    private DutyLabels() {}

    public static String kindName(String kind) {
        return "OVT".equals(kind) ? "ÕVT" : "SVT";
    }

    public static String label(Duty d) {
        return kindName(d.getKind()) + " " + hm(d.getStarts()) + "–" + hm(d.getEnds());
    }

    public static String labelWithPosition(Duty d) {
        return d.getPosition().isBlank() ? label(d) : label(d) + " · pos " + d.getPosition();
    }

    private static String hm(LocalTime t) {
        return t.getHour() + ":" + String.format("%02d", t.getMinute());
    }
}
```

- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: Read surnames from duty cells`.

---

### Task 4: DutyExcelParser

**Files:**
- Create: `api/src/main/java/com/asendus/api/duty/{DutyExcelParser,ParsedDuties}.java`
- Test: `api/src/test/java/com/asendus/api/duty/DutyExcelParserTest.java`

The sheet layout and rules are in the spec ("The Excel" and "Reading rules"). Columns: A=0 day letter, B=1 class group, C=2 ÕVT time, D=3 SVT start, E=4 ÕVT range or note, F/G=5/6 and H/I=7/8 names/position.

- [ ] **Step 1: Failing test** — a made-up sheet that copies every tricky pattern of the real one (names are fake)

```java
class DutyExcelParserTest {
    static ParsedDuties parsed;

    @BeforeAll static void parse() {
        byte[] xlsx = new TestDutySheet()
                .day("E")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", "Kask, Tamm", 4, "Mänd, Saar", 5)
                .group("II klassid", "", LocalTime.of(10, 45), "11:05-11:30", "Kuusk", 6, "Lepp", 4)
                .group("III klassid", "10:25", LocalTime.of(11, 5), "UJUMAS 3.1-3.2", "", null, "Pihlakas", 1)
                .group("VI klassid", "", LocalTime.of(12, 10), "klj 12:30-13:15", "sööklas: klassijuhataja", null, "", null)
                .group("IV klassid", "", LocalTime.of(13, 0), "klj 12:15-12:55", "", null, "4.S & 6.R ÕVT - Vaher", 1)
                .blank()
                .day("T")
                .group("III klassid", "10:25-11:05", LocalTime.of(11, 5), "", "Kask", 1, "", null)
                .group("VI klassid", "", LocalTime.of(12, 10), "", "sööklas: Tamm, Mänd", null, "", null)
                .group("V klassid", "", LocalTime.of(12, 35), "12:55- 13:20", "Kuusk", 1, "", null)
                .group("IV klassid", "12:15-12:55", LocalTime.of(12, 55), "", "Lepp // 4.s klj tund (315)", 1, "", null)
                .blank()
                .day("K")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", "Kask", 5, "", null)
                .blank()
                .day("N")
                .group("VI klassid", "", LocalTime.of(12, 10), "",
                        "sööklas: Saar, Vaher // 6.S ÕVT (12:35 -13:10) - pos 4 (Kask/Tamm)", null, "", null)
                .group("V klassid", "", LocalTime.of(12, 35), "12:55- 13:20", "", null, "Lepp (õvt +6.S)", 4)
                .blank()
                .day("R")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", "Tamm", 5, "", null)
                .bytes();
        parsed = new DutyExcelParser().parse(xlsx);
    }

    static List<ParsedDuties.ParsedDuty> of(String surname, int weekday) {
        return parsed.duties().stream().filter(d -> d.surname().equals(surname) && d.weekday() == weekday).toList();
    }

    @Test void outdoorDutyWithNumericPosition() {
        assertThat(of("Kask", 1)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo("OVT");
            assertThat(d.starts()).isEqualTo(LocalTime.of(10, 45));
            assertThat(d.ends()).isEqualTo(LocalTime.of(11, 15));
            assertThat(d.period()).isEqualTo(3);
            assertThat(d.classGroup()).isEqualTo("I klassid");
            assertThat(d.position()).isEqualTo("4");
        });
        assertThat(of("Saar", 1)).singleElement().satisfies(d -> assertThat(d.position()).isEqualTo("5"));
    }

    @Test void spacedRangeIsRead() {
        assertThat(of("Kuusk", 2)).singleElement().satisfies(d -> {
            assertThat(d.starts()).isEqualTo(LocalTime.of(12, 55));
            assertThat(d.period()).isEqualTo(5);
        });
    }

    @Test void rangeFromColumnCWhenEIsEmpty() {
        assertThat(of("Kask", 2)).singleElement().satisfies(d -> {
            assertThat(d.starts()).isEqualTo(LocalTime.of(10, 25));
            assertThat(d.period()).isEqualTo(3);
        });
    }

    @Test void canteenDutyLasts45MinutesFromTheTimeCell() {
        assertThat(of("Tamm", 2)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo("SVT");
            assertThat(d.starts()).isEqualTo(LocalTime.of(12, 10));
            assertThat(d.ends()).isEqualTo(LocalTime.of(12, 55));
            assertThat(d.period()).isEqualTo(5);
        });
        assertThat(of("Mänd", 2)).hasSize(1);
    }

    @Test void cutsNotesButKeepsTheName() {
        assertThat(of("Lepp", 2)).singleElement().satisfies(d -> assertThat(d.period()).isEqualTo(5));
        assertThat(of("Lepp", 4)).hasSize(1);
        assertThat(of("Saar", 4)).hasSize(1);
        assertThat(of("Vaher", 4)).hasSize(1);
    }

    @Test void everyWeekdayAndNoPlaceholderNames() {
        assertThat(parsed.duties()).extracting(ParsedDuties.ParsedDuty::weekday).contains(1, 2, 3, 4, 5);
        assertThat(parsed.duties()).noneMatch(d -> d.surname().equalsIgnoreCase("klassijuhataja"));
    }

    @Test void warnsInsteadOfGuessing() {
        assertThat(parsed.warnings())
                .anySatisfy(w -> assertThat(w).startsWith("E, III klassid").contains("Pihlakas"))  // only "10:25"
                .anySatisfy(w -> assertThat(w).startsWith("E, IV klassid").contains("Vaher"))      // E = klj…, C empty
                .anySatisfy(w -> assertThat(w).contains("klassijuhataja"))
                .anySatisfy(w -> assertThat(w).startsWith("N, VI klassid").contains("6.S ÕVT"))    // hidden after //
                .noneSatisfy(w -> assertThat(w).startsWith("T, IV klassid"));                     // "4.s klj tund" is fine
        assertThat(of("Pihlakas", 1)).isEmpty();
        assertThat(of("Vaher", 1)).isEmpty();
    }

    @Test void rejectsOtherFiles() {
        assertThatThrownBy(() -> new DutyExcelParser().parse("not excel".getBytes()))
                .isInstanceOf(DutyExcelParser.NotADutySheetException.class)
                .hasMessage("See ei tundu olevat vahetunni valvete tabel");
        byte[] noHeaders = new TestDutySheet().group("I klassid", "", null, "10:45-11:15", "Kask", 1, "", null).bytes();
        assertThatThrownBy(() -> new DutyExcelParser().parse(noHeaders))
                .isInstanceOf(DutyExcelParser.NotADutySheetException.class);
    }
}
```

**Local check against the real file (not committed):** after Step 4, run the parser once on the school's Excel from
a scratch `jshell`/throwaway test outside the repo, or temporarily with
`DUTY_XLSX=<path> ./mvnw -q test -Dtest=RealDutySheetCheck` using a test annotated
`@EnabledIfEnvironmentVariable(named = "DUTY_XLSX", matches = ".+")` that only prints `duties().size()` and the
warnings. Expect ~60 duties and warnings for the Monday III and IV rows, the "klassijuhataja" rows and the Thursday
`6.S ÕVT` cell. Delete the throwaway test before committing; it must contain no names.

- [ ] **Step 2: Run** → FAIL.

- [ ] **Step 3: Implement**

```java
package com.asendus.api.duty;

import java.time.LocalTime;
import java.util.List;

public record ParsedDuties(List<ParsedDuty> duties, List<String> warnings) {
    /** One teacher (by surname, as written in the sheet) on one duty. kind is "OVT" or "SVT". */
    public record ParsedDuty(int weekday, int period, String kind, LocalTime starts, LocalTime ends,
                             String classGroup, String position, String surname) {}
}
```

```java
package com.asendus.api.duty;

import com.asendus.api.duty.ParsedDuties.ParsedDuty;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the break duties (ÕVT outdoor, SVT canteen) from the first sheet of the school's
 * "SVT, ÕVT, muud graafikud" Excel. Anything it can't read safely becomes a warning, never a guess.
 */
@Component
public class DutyExcelParser {
    public static final int CANTEEN_MINUTES = 45;
    private static final List<String> DAYS = List.of("E", "T", "K", "N", "R");
    private static final int A = 0, B = 1, C = 2, D = 3, E = 4;
    private static final int[][] HALVES = {{5, 6}, {7, 8}}; // names, position: F/G and H/I
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    public static class NotADutySheetException extends RuntimeException {
        public NotADutySheetException() {
            super("See ei tundu olevat vahetunni valvete tabel");
        }
    }

    public ParsedDuties parse(byte[] xlsx) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            return parse(wb.getSheetAt(0));
        } catch (NotADutySheetException e) {
            throw e;
        } catch (Exception e) {
            throw new NotADutySheetException();
        }
    }

    private ParsedDuties parse(Sheet sheet) {
        DataFormatter fmt = new DataFormatter();
        List<ParsedDuty> duties = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean sawHeader = false;
        int weekday = 0;
        String day = "";
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) { weekday = 0; continue; }
            String a = text(fmt, row, A);
            if (DAYS.contains(a) && text(fmt, row, C).equals("ÕVT")) {
                weekday = DAYS.indexOf(a) + 1;
                day = a;
                sawHeader = true;
                continue;
            }
            if (weekday == 0) continue;
            String group = text(fmt, row, B);
            if (group.isEmpty()) { weekday = 0; continue; } // block ends
            for (int[] half : HALVES) {
                readHalf(fmt, row, half, weekday, day + ", " + group, group, duties, warnings);
            }
        }
        if (!sawHeader) throw new NotADutySheetException();
        return new ParsedDuties(duties, warnings);
    }

    private void readHalf(DataFormatter fmt, Row row, int[] half, int weekday, String where, String group,
                          List<ParsedDuty> duties, List<String> warnings) {
        String names = text(fmt, row, half[0]);
        if (names.isEmpty()) return;
        String at = where + ": „" + names + "“";

        String kind;
        LocalTime start, end;
        if (DutyNames.isCanteen(names)) {
            Optional<LocalTime> t = firstTime(text(fmt, row, D));
            if (t.isEmpty()) { warnings.add(at + " – söögivahetunni kellaaeg puudub"); return; }
            kind = "SVT";
            start = t.get();
            end = start.plusMinutes(CANTEEN_MINUTES);
        } else {
            String e = DutyNames.lower(text(fmt, row, E));
            Optional<LocalTime[]> range = e.startsWith("klj") || e.startsWith("ujumas") ? Optional.empty() : range(e);
            if (range.isEmpty()) range = range(text(fmt, row, C));
            if (range.isEmpty()) { warnings.add(at + " – õuevahetunni kellaaeg puudub"); return; }
            kind = "OVT";
            start = range.get()[0];
            end = range.get()[1];
        }
        int period = PeriodTimes.periodOf(start, end);
        if (period == 0) { warnings.add(at + " – kellaaeg jääb tundidest välja"); return; }
        if (DutyNames.hidesDuty(names)) warnings.add(at + " – siin võib olla veel üks valve, kontrolli käsitsi");

        String position = text(fmt, row, half[1]);
        for (String surname : DutyNames.surnames(names)) {
            if (surname.equalsIgnoreCase("klassijuhataja")) {
                warnings.add(at + " – klassijuhatajat ei saa nime järgi leida");
            } else {
                duties.add(new ParsedDuty(weekday, period, kind, start, end, group, position, surname));
            }
        }
    }

    private static Optional<LocalTime[]> range(String s) {
        Matcher m = DutyNames.RANGE.matcher(s);
        if (!m.find()) return Optional.empty();
        return Optional.of(new LocalTime[]{time(m.group(1), m.group(2)), time(m.group(3), m.group(4))});
    }

    private static Optional<LocalTime> firstTime(String s) {
        Matcher m = TIME.matcher(s);
        return m.find() ? Optional.of(time(m.group(1), m.group(2))) : Optional.empty();
    }

    private static LocalTime time(String h, String m) {
        return LocalTime.of(Integer.parseInt(h), Integer.parseInt(m));
    }

    /** The cell as shown in Excel (times as "10:25"), NFC, trimmed; "" when missing. */
    private static String text(DataFormatter fmt, Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return Normalizer.normalize(fmt.formatCellValue(cell), Normalizer.Form.NFC).replace(' ', ' ').trim();
    }
}
```

- [ ] **Step 4: Run** → PASS. Then do the local real-file check above.
- [ ] **Step 5: Commit** — `feat: Parse break duties from Excel`.

---

### Task 5: DutyService and DutyController

**Files:**
- Create: `api/src/main/java/com/asendus/api/duty/{DutyService,DutyController,DutyDtos}.java`
- Modify: `api/src/main/java/com/asendus/api/timetable/TimetableService.java` (add `activeTeachers()`)
- Test: `api/src/test/java/com/asendus/api/duty/DutyControllerTest.java`

- [ ] **Step 1: Failing integration test.** Setup as in `DayControllerTest` (truncate, upload `opetajad-b.pdf`,
  activate). Surnames come from the timetable at runtime; the only literal names are made up.

```java
class DutyControllerTest extends IntegrationTest {
    static final String NOBODY = "Väljamõeldud"; // matches no teacher
    @Autowired JdbcTemplate jdbc;
    String unique; // surname of exactly one teacher
    String shared; // surname we give to a second, made-up teacher

    @BeforeEach void setUp() throws Exception {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, absence, duty_version CASCADE");
        uploadTimetable();
        List<String> surnames = jdbc.queryForList("""
                select split_part(t.name, ' ', 1) s from teacher t
                join timetable_version v on v.id = t.version_id and v.active
                group by s having count(*) = 1 order by s""", String.class);
        unique = surnames.get(0);
        shared = surnames.get(1);
        jdbc.update("insert into teacher (version_id, key, name) select id, ?, ? from timetable_version where active",
                TeacherKey.of(shared + " Testnimi"), shared + " Testnimi");
    }

    void uploadTimetable() throws Exception { /* POST /timetables with opetajad-b.pdf + activate, as in DayControllerTest */ }

    byte[] sheet() {
        return new TestDutySheet().day("R")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", unique + ", " + NOBODY, 5, shared, 4)
                .group("III klassid", "10:25", null, "", "Kask", 1, "", null) // a single time → warning
                .bytes();
    }

    ResultActions upload(byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/duties")
                .file(new MockMultipartFile("file", "valved.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes))
                .header("Authorization", bearer()));
    }

    String uploadJson() throws Exception {
        return upload(sheet()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    long unresolvedId(String json, String rawName) {
        List<Number> ids = JsonPath.read(json, "$.unresolved[?(@.rawName=='" + rawName + "')].id");
        return ids.get(0).longValue();
    }

    @Test void uploadMatchesSurnamesAndListsTheRest() throws Exception {
        String json = uploadJson();
        assertThat((String) JsonPath.read(json, "$.fileName")).isEqualTo("valved.xlsx");
        assertThat((Integer) JsonPath.read(json, "$.dutyCount")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(json, "$.teacherCount")).isEqualTo(1);
        assertThat((List<String>) JsonPath.read(json, "$.warnings")).anySatisfy(w -> assertThat(w).contains("III klassid"));
        assertThat((List<?>) JsonPath.read(json, "$.unresolved[?(@.rawName=='" + NOBODY + "')].candidates[*]")).isEmpty();
        assertThat((List<?>) JsonPath.read(json, "$.unresolved[?(@.rawName=='" + shared + "')].candidates[*]")).hasSize(2);
        assertThat(jdbc.queryForObject("select teacher_key from duty where raw_name = ?", String.class, unique))
                .isEqualTo(jdbc.queryForObject("""
                        select t.key from teacher t join timetable_version v on v.id = t.version_id and v.active
                        where split_part(t.name, ' ', 1) = ?""", String.class, unique));
    }

    @Test void getReturnsTheSummaryOrNoContent() throws Exception {
        mockMvc.perform(get("/duties").header("Authorization", bearer())).andExpect(status().isNoContent());
        uploadJson();
        mockMvc.perform(get("/duties").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dutyCount").value(1));
    }

    @Test void resolveAndSkip() throws Exception {
        String json = uploadJson();
        String key = JsonPath.<List<String>>read(json, "$.unresolved[?(@.rawName=='" + shared + "')].candidates[0].key").get(0);
        mockMvc.perform(put("/duties/{id}", unresolvedId(json, shared)).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teacherKey\":\"" + key + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dutyCount").value(2));
        mockMvc.perform(delete("/duties/{id}", unresolvedId(json, NOBODY)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unresolved", empty()));
    }

    @Test void resolveRejectsUnknownTeacherAndResolvedDuty() throws Exception {
        String json = uploadJson();
        mockMvc.perform(put("/duties/{id}", unresolvedId(json, NOBODY)).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teacherKey\":\"keegi muu\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Tundmatu õpetaja"));
        long resolved = jdbc.queryForObject("select id from duty where teacher_key is not null", Long.class);
        mockMvc.perform(delete("/duties/{id}", resolved).header("Authorization", bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Valve on juba määratud"));
    }

    @Test void newUploadReplacesTheOld() throws Exception {
        uploadJson();
        uploadJson();
        assertThat(jdbc.queryForObject("select count(*) from duty_version", Integer.class)).isEqualTo(1);
    }

    @Test void newTimetableKeepsDuties() throws Exception {
        uploadJson();
        uploadTimetable();
        mockMvc.perform(get("/duties").header("Authorization", bearer())).andExpect(jsonPath("$.dutyCount").value(1));
    }

    @Test void rejectsOtherFilesAndNeedsATimetable() throws Exception {
        upload("hello".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("See ei tundu olevat vahetunni valvete tabel"));
        jdbc.execute("TRUNCATE timetable_version CASCADE");
        upload(sheet()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Lae esmalt tunniplaan üles"));
    }
}
```

- [ ] **Step 2: Run** → FAIL.

- [ ] **Step 3: DTOs**

```java
package com.asendus.api.duty;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class DutyDtos {
    private DutyDtos() {}

    /** dutyCount / teacherCount count matched duties only. */
    public record DutySummaryDto(String fileName, Instant uploadedAt, int dutyCount, int teacherCount,
                                 List<String> warnings, List<UnresolvedDutyDto> unresolved) {}

    /** A duty whose surname matched several teachers (candidates) or none (empty). label e.g. "ÕVT 10:45–11:15". */
    public record UnresolvedDutyDto(Long id, int weekday, String label, String classGroup, String rawName,
                                    List<TeacherOptionDto> candidates) {}

    public record TeacherOptionDto(String key, String name) {}

    public record ResolveDutyRequest(@NotBlank String teacherKey) {}
}
```

- [ ] **Step 4: `TimetableService.activeTeachers()`**

```java
    /** The active version's teachers in name order; empty if there is no active version. */
    @Transactional(readOnly = true)
    public List<Teacher> activeTeachers() {
        return activeVersion().map(v -> teacherRepository.findByVersionIdOrderByName(v.getId())).orElse(List.of());
    }
```

- [ ] **Step 5: Service**

```java
package com.asendus.api.duty;

import com.asendus.api.duty.DutyDtos.*;
import com.asendus.api.duty.ParsedDuties.ParsedDuty;
import com.asendus.api.timetable.Teacher;
import com.asendus.api.timetable.TeacherKey;
import com.asendus.api.timetable.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DutyService {
    private final DutyExcelParser parser;
    private final DutyVersionRepository versionRepository;
    private final DutyRepository dutyRepository;
    private final TimetableService timetableService;

    /** Replaces the duties with the Excel's, matching surnames to the active timetable's teachers. */
    @Transactional
    public DutySummaryDto upload(String fileName, byte[] xlsx, Long userId) {
        ParsedDuties parsed;
        try {
            parsed = parser.parse(xlsx);
        } catch (DutyExcelParser.NotADutySheetException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        List<Teacher> teachers = timetableService.activeTeachers();
        if (teachers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lae esmalt tunniplaan üles");
        }
        Map<String, List<Teacher>> bySurname = teachers.stream()
                .collect(Collectors.groupingBy(t -> fold(surnameOf(t.getName()))));

        versionRepository.deleteAllInBatch(); // the database cascades to the old duties
        DutyVersion v = versionRepository.save(DutyVersion.builder()
                .uploadedBy(userId).fileName(fileName).warnings(parsed.warnings()).build());
        dutyRepository.saveAll(parsed.duties().stream().map(p -> toDuty(v.getId(), p, bySurname)).toList());
        return summary(v);
    }

    @Transactional(readOnly = true)
    public Optional<DutySummaryDto> current() {
        return versionRepository.findFirstByOrderByIdDesc().map(this::summary);
    }

    /** Sets the teacher of a duty whose surname didn't match exactly one teacher. */
    @Transactional
    public DutySummaryDto resolve(Long id, String teacherKey) {
        Duty d = unresolved(id);
        Teacher t = timetableService.activeTeacher(teacherKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tundmatu õpetaja"));
        d.setTeacherKey(t.getTeacherKey());
        d.setTeacherName(TeacherKey.displayName(t.getName()));
        d.setCandidateKeys(new ArrayList<>());
        dutyRepository.save(d);
        return summary(versionRepository.findById(d.getVersionId()).orElseThrow());
    }

    /** Drops a duty, e.g. a teaching assistant who isn't in the teacher timetable. */
    @Transactional
    public DutySummaryDto skip(Long id) {
        Duty d = unresolved(id);
        dutyRepository.delete(d);
        dutyRepository.flush();
        return summary(versionRepository.findById(d.getVersionId()).orElseThrow());
    }

    /** Matched duties on the weekday (1 = Monday … 5 = Friday), by teacher key. Empty if none uploaded. */
    @Transactional(readOnly = true)
    public Map<String, List<Duty>> dutiesOn(int weekday) {
        return versionRepository.findFirstByOrderByIdDesc()
                .map(v -> dutyRepository.findByVersionIdAndWeekdayAndTeacherKeyNotNull(v.getId(), (short) weekday).stream()
                        .collect(Collectors.groupingBy(Duty::getTeacherKey)))
                .orElse(Map.of());
    }

    /** Only duties whose surname didn't match can be resolved or skipped. */
    private Duty unresolved(Long id) {
        Duty d = dutyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Valvet ei leitud"));
        if (d.getTeacherKey() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valve on juba määratud");
        }
        return d;
    }

    private static Duty toDuty(Long versionId, ParsedDuty p, Map<String, List<Teacher>> bySurname) {
        List<Teacher> matches = bySurname.getOrDefault(fold(p.surname()), List.of());
        Duty.DutyBuilder b = Duty.builder().versionId(versionId)
                .weekday((short) p.weekday()).period((short) p.period()).kind(p.kind())
                .starts(p.starts()).ends(p.ends()).classGroup(p.classGroup()).position(p.position())
                .rawName(p.surname());
        if (matches.size() == 1) {
            Teacher t = matches.get(0);
            b.teacherKey(t.getTeacherKey()).teacherName(TeacherKey.displayName(t.getName()));
        } else {
            b.candidateKeys(new ArrayList<>(matches.stream().map(Teacher::getTeacherKey).toList()));
        }
        return b.build();
    }

    private DutySummaryDto summary(DutyVersion v) {
        List<Duty> all = dutyRepository.findByVersionIdOrderByWeekdayAscPeriodAscIdAsc(v.getId());
        Map<String, String> names = timetableService.activeTeachers().stream()
                .collect(Collectors.toMap(Teacher::getTeacherKey, t -> TeacherKey.displayName(t.getName()), (a, b) -> a));
        List<Duty> matched = all.stream().filter(d -> d.getTeacherKey() != null).toList();
        List<UnresolvedDutyDto> unresolved = all.stream().filter(d -> d.getTeacherKey() == null)
                .map(d -> new UnresolvedDutyDto(d.getId(), d.getWeekday(), DutyLabels.label(d), d.getClassGroup(),
                        d.getRawName(), d.getCandidateKeys().stream().filter(names::containsKey)
                                .map(k -> new TeacherOptionDto(k, names.get(k))).toList()))
                .toList();
        int teacherCount = (int) matched.stream().map(Duty::getTeacherKey).distinct().count();
        return new DutySummaryDto(v.getFileName(), v.getUploadedAt(), matched.size(), teacherCount,
                v.getWarnings(), unresolved);
    }

    /** aSc prints "Surname Firstname(s)". */
    private static String surnameOf(String ascName) {
        return ascName.trim().split("\\s+")[0];
    }

    /** Lowercase without diacritics, so "Pärnpuu" and "parnpuu" match. */
    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 6: Controller** (mirror `TimetableController.upload` for the file checks)

```java
@RestController
@RequiredArgsConstructor
public class DutyController {
    private final DutyService dutyService;

    @PostMapping("/duties")
    @ResponseStatus(HttpStatus.CREATED)
    public DutySummaryDto upload(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fail puudub");
        }
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "valved.xlsx";
        return dutyService.upload(fileName, file.getBytes(), JwtUtils.extractUserId(jwt));
    }

    @GetMapping("/duties")
    public ResponseEntity<DutySummaryDto> current() {
        return dutyService.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/duties/{id}")
    public DutySummaryDto resolve(@PathVariable Long id, @Valid @RequestBody ResolveDutyRequest request) {
        return dutyService.resolve(id, request.teacherKey());
    }

    @DeleteMapping("/duties/{id}")
    public DutySummaryDto skip(@PathVariable Long id) {
        return dutyService.skip(id);
    }
}
```

- [ ] **Step 7: Run** `DutyControllerTest` → PASS; whole suite → PASS.
- [ ] **Step 8: Commit** — `feat: Add break duty upload endpoints`.

---

### Task 6: Duties in candidates and absences

**Files:**
- Modify: `api/src/main/java/com/asendus/api/substitution/{SubstitutionService,DayDtos}.java`
- Test: `api/src/test/java/com/asendus/api/substitution/DutyDayTest.java`

- [ ] **Step 1: Failing test** (`DutyDayTest extends IntegrationTest`, Friday `2026-09-04`). Copy the helpers
  `postAbsence`, `candidatesJson`, `FRI`, `uploadTimetable` from `DayControllerTest`/`DutyControllerTest`.
  Setup picks two period-3 candidates with unique surnames from the real timetable at runtime, gives the first a
  duty and makes the second's surname ambiguous:

```java
    String onDuty;    // key of a period-3 candidate whose Friday duty is matched
    String ambiguous; // key of a period-3 candidate whose surname is shared with a made-up teacher

    String surnameOf(String key) {
        return jdbc.queryForObject("""
                select split_part(t.name, ' ', 1) from teacher t
                join timetable_version v on v.id = t.version_id and v.active where t.key = ?""", String.class, key);
    }

    boolean uniqueSurname(String surname) {
        return jdbc.queryForObject("""
                select count(*) from teacher t join timetable_version v on v.id = t.version_id and v.active
                where split_part(t.name, ' ', 1) = ?""", Integer.class, surname) == 1;
    }

    @BeforeEach void setUp() throws Exception {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, absence, duty_version CASCADE");
        uploadTimetable();
        List<String> keys = JsonPath.<List<String>>read(candidatesJson(3), "$[*].key").stream()
                .filter(k -> uniqueSurname(surnameOf(k))).toList();
        assertThat(keys).hasSizeGreaterThanOrEqualTo(2);
        onDuty = keys.get(0);
        ambiguous = keys.get(1);
        String shared = surnameOf(ambiguous);
        jdbc.update("insert into teacher (version_id, key, name) select id, ?, ? from timetable_version where active",
                TeacherKey.of(shared + " Testnimi"), shared + " Testnimi");
        byte[] xlsx = new TestDutySheet().day("R")
                .group("I klassid", "", LocalTime.of(10, 25), "10:45-11:15", surnameOf(onDuty), 5, shared, 4)
                .bytes();
        mockMvc.perform(multipart("/duties").file(new MockMultipartFile("file", "valved.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                .header("Authorization", bearer())).andExpect(status().isCreated());
    }

    static final String LABEL_ROWS = "$.rows[?(@.dutyLabel == 'ÕVT 10:45–11:15 · pos 5')]";

    @Test void absentTeachersDutyBecomesARow() throws Exception {
        postAbsence(FRI, "{\"teacherKey\":\"" + onDuty + "\"}").andExpect(status().isCreated())
                .andExpect(jsonPath(LABEL_ROWS + ".period", contains(3)))
                .andExpect(jsonPath(LABEL_ROWS + ".classes", contains("I klassid")))
                .andExpect(jsonPath(LABEL_ROWS + ".subject", contains("ÕVT")))
                .andExpect(jsonPath("$.rows[?(@.dutyLabel == null)]", not(empty()))); // the lessons
    }

    @Test void dutyOutsideAPartialAbsenceIsLeftOut() throws Exception {
        postAbsence(FRI, "{\"teacherKey\":\"" + onDuty + "\",\"fromPeriod\":4,\"toPeriod\":7}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rows[?(@.dutyLabel != null)]", empty()));
    }

    @Test void candidateWithADutyGetsALabelAndSortsLastInItsGroup() throws Exception {
        String json = candidatesJson(3);
        List<Map<String, Object>> all = JsonPath.read(json, "$[*]");
        Map<String, Object> me = all.stream().filter(c -> c.get("key").equals(onDuty)).findFirst().orElseThrow();
        assertThat((List<?>) me.get("duties")).containsExactly("ÕVT 10:45–11:15");
        List<Map<String, Object>> sameStatus = all.stream().filter(c -> c.get("status").equals(me.get("status"))).toList();
        assertThat(sameStatus.get(sameStatus.size() - 1).get("key")).isEqualTo(onDuty);
        assertThat(all).filteredOn(c -> !c.get("key").equals(onDuty))
                .allSatisfy(c -> assertThat((List<?>) c.get("duties")).isEmpty());
    }

    @Test void unresolvedDutyCountsOnlyAfterItIsResolved() throws Exception {
        assertThat(JsonPath.<List<List<?>>>read(candidatesJson(3), "$[?(@.key=='" + ambiguous + "')].duties").get(0)).isEmpty();
        long id = jdbc.queryForObject("select id from duty where teacher_key is null", Long.class);
        mockMvc.perform(put("/duties/{id}", id).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"teacherKey\":\"" + ambiguous + "\"}"))
                .andExpect(status().isOk());
        assertThat(JsonPath.<List<List<?>>>read(candidatesJson(3), "$[?(@.key=='" + ambiguous + "')].duties").get(0))
                .containsExactly("ÕVT 10:45–11:15");
    }
```

- [ ] **Step 2: Run** → FAIL.

- [ ] **Step 3: DTOs** — `SubstitutionDto` gains `String dutyLabel` (last component); `CandidateDto` gains `List<String> duties` (last component). Update `toDto(Substitution)` to pass `s.getDutyLabel()` and the doc comments.

- [ ] **Step 4: SubstitutionService**

Inject `DutyService dutyService`. Sort order:

```java
    private static final Comparator<CandidateDto> CANDIDATE_ORDER = Comparator
            .comparing(CandidateDto::status)               // FREE before ASK_FIRST (enum order)
            .thenComparing(c -> !c.duties().isEmpty())     // a break duty in this period → after the others
            .thenComparingInt(CandidateDto::todayCount)
            .thenComparing(CandidateDto::name, TeacherKey.DISPLAY_ORDER);
```

In `candidates(...)`:
```java
        Map<String, List<Duty>> duties = dutyService.dutiesOn(weekday);
        ...
                List<String> dutyLabels = duties.getOrDefault(key, List.of()).stream()
                        .filter(d -> d.getPeriod() == period).map(DutyLabels::label).toList();
                out.add(new CandidateDto(key, ..., todayCounts.getOrDefault(key, 0), dutyLabels));
```

In `createAbsence(...)` replace the single `saveAll` with lesson rows followed by duty rows (ids keep lessons before duties within a period):
```java
        List<Substitution> newRows = new ArrayList<>(lessons.stream()
                .filter(l -> l.getPeriod() >= from && l.getPeriod() <= to)
                .map(l -> Substitution.builder().absenceId(absence.getId()).period(l.getPeriod())
                        .classes(l.getClasses()).subject(l.getSubject()).build())
                .toList());
        dutyService.dutiesOn(weekday).getOrDefault(teacher.getTeacherKey(), List.of()).stream()
                .filter(d -> d.getPeriod() >= from && d.getPeriod() <= to)
                .map(d -> Substitution.builder().absenceId(absence.getId()).period(d.getPeriod())
                        .classes(d.getClassGroup()).subject(DutyLabels.kindName(d.getKind()))
                        .dutyLabel(DutyLabels.labelWithPosition(d)).build())
                .forEach(newRows::add);
        List<Substitution> rows = substitutionRepository.saveAll(newRows);
```
Update the method's doc comment: "…one row per lesson and per break duty of theirs in the period range."

- [ ] **Step 5: Run** `DutyDayTest`, `DayControllerTest` → PASS; whole suite → PASS.
- [ ] **Step 6: Commit** — `feat: Show break duties in candidates and absences`.

---

### Task 7: Web — duty upload card with "Vali õpetaja"

**Files:**
- Modify: `web/src/api/types.ts`, `web/src/api/queries.ts`
- Create: `web/src/pages/timetable/DutyCard.tsx`
- Modify: `web/src/pages/TimetablePage.tsx` (render `<DutyCard />` right after `<UploadCard />`)

- [ ] **Step 1: Types**

```ts
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
```
`Substitution` gains `dutyLabel: string | null`; `Candidate` gains `duties: string[]` (required — update any
`Candidate`/`Substitution` literal in tests so `tsc -b` passes).

- [ ] **Step 2: Query**

```ts
/** The uploaded break duties, or null when none have been uploaded yet (204). */
export async function fetchDuties(): Promise<DutySummary | null> {
  return (await api<DutySummary | undefined>('/duties')) ?? null
}
```

- [ ] **Step 3: `DutyCard.tsx`** — same look as `UploadCard` (dashed drop area, `Stat` pills, orange warning list). Behaviour:
  - `useQuery(['duties'], fetchDuties)`; the upload, resolve and skip mutations all `qc.setQueryData(['duties'], summary)` with the returned summary and invalidate `['candidates']` and `['day']`.
  - Drop area text: title "Vahetunni valved", button text "Vali valvete Excel", caption "SVT, ÕVT graafik (.xlsx)", `accept=".xlsx"`.
  - When data: file name + date (`toLocaleDateString('et-EE')`), pills `{dutyCount} valvet`, `{teacherCount} õpetajat`, `{warnings.length} hoiatust` (warn), then the warnings.
  - `unresolved.length > 0`: heading **Vali õpetaja**, hint "Neid nimesid ei leitud tunniplaanist üheselt. Kuni õpetaja on valimata, valvet ei arvestata." Then per duty a row: `{'ETKNR'[weekday-1]} · {label} · {classGroup} · „{rawName}“`, a `<select className="field">` with a placeholder option "Vali õpetaja…" and either `candidates` (when non-empty) or all teachers from the `['timetable']` query (`fetchActiveTimetable`, `teachers` → `{key, displayName}`); `onChange` → `PUT /duties/{id}` with `{ teacherKey }`. Next to it a text action "Jäta vahele" (`hover:underline`, like "Eemalda") → `DELETE /duties/{id}`.
  - Use `<Card>` and `cn` like `UploadCard`; keep every tap target ≥ 44px (`min-h-11`).

- [ ] **Step 4: Run** `cd web && npx tsc -b && npm test -- --run` → PASS. Check in the browser (`npm run dev`, API on :8080 with the dev profile): upload the school's Excel from `~/Downloads` (never copy it into the repo) after a timetable; the names the timetable doesn't have (teaching assistants, a name the PDF spells differently) appear under "Vali õpetaja" with all teachers in the select; picking one removes the row and raises the duty count; "Jäta vahele" removes a row.
- [ ] **Step 5: Commit** — `feat: Add break duty upload card`.

---

### Task 8: Web — badges, duty rows, table

**Files:**
- Modify: `web/src/pages/day/CandidatePicker.tsx`, `web/src/pages/day/AbsenceCard.tsx`, `web/src/lib/dayTable.ts`
- Test: `web/src/lib/dayTable.test.ts`

- [ ] **Step 1: Failing test** in `dayTable.test.ts`: a day with one absence whose rows are a lesson (`subject: 'Ma'`, `dutyLabel: null`) and a duty (`classes: 'I klassid'`, `subject: 'ÕVT'`, `dutyLabel: 'ÕVT 10:45–11:15 · pos 4'`, `period: 3`). Expect the duty's table row to have `'I klassid'` as class and `'ÕVT 10:45–11:15 · pos 4'` in the subject column ("Õppeaine"), and the lesson row `'matemaatika'`. Update existing fixtures in the test file to include `dutyLabel: null`.

- [ ] **Step 2: Run** → FAIL.

- [ ] **Step 3: dayTable.ts** — subject column: `r.dutyLabel ?? subjectName(r.subject)`.

- [ ] **Step 4: AbsenceCard `Row`** — when `row.dutyLabel`: second text is `row.dutyLabel` instead of `subjectName(row.subject)`, and the `<li>` gets a violet left accent (`border-l-8 border-l-violet-500`) so duty rows stand out from lessons.

- [ ] **Step 5: CandidatePicker `CandidateButton`** — under the name, one violet pill per label: `<span className="mt-1 mr-1 inline-block rounded-full bg-violet-500 px-2 py-0.5 text-xs font-bold text-white">{d}</span>`. Do not nest an InfoTip inside the button. Instead, when any candidate in the list has duties, render below the groups: `<p className="flex items-center gap-2 text-sm text-text-caption"><span className="h-3 w-3 rounded-full bg-violet-500" /> on sel ajal vahetunni valves <InfoTip label="Vahetunni valve">Õpetajal on selle tunni ajal õue- või söögivahetunni valve. Kui ta asendab, peab valve jaoks kellegi teise leidma.</InfoTip></p>`.

- [ ] **Step 6: Run** `npx tsc -b && npm test -- --run` → PASS. Browser: mark a teacher with a Friday ÕVT in the Excel absent → a violet-accented duty row (e.g. "I klassid · ÕVT 10:45–11:15 · pos 5") in period 3; open a period-3 row → duty teachers carry the badge and sit last in their group.
- [ ] **Step 7: Commit** — `feat: Show break duties on the day page`.

---

### Task 9: Help text

**Files:**
- Modify: `web/src/help/guideSections.tsx`, `web/src/pages/GuidePage.tsx`, `web/src/help/HelpButton.tsx`

- [ ] **Step 1:** Add to `guideSections.tsx` (same components as the other sections; Estonian, no jargon):

```tsx
/** Optional: break duties from the Excel. */
export function DutyHelp() {
  return (
    <Card className="space-y-3">
      <h2 className="text-xl font-extrabold">Vahetunni valved</h2>
      <p>
        Õue- ja söögivahetunni valved on tunniplaanis tühjad augud, aga õpetaja on siis tegelikult hõivatud. Lae{' '}
        <Link to="/tunniplaan" className="font-semibold text-casual-sky-blue-darker hover:underline">Tunniplaani</Link>{' '}
        lehel üles valvete Excel (SVT, ÕVT graafik), siis arvestab rakendus neid.
      </p>
      <Legend dot="bg-violet-500" title="Lilla silt asendaja juures">
        Õpetajal on selle tunni ajal valve. Teda pakutakse ikka, aga teistest tagapool.
      </Legend>
      <p>Kui puuduval õpetajal on valve, tuleb tema tundide juurde lilla rida ka valve jaoks.</p>
      <Tip>
        Kui nime ei leitud tunniplaanist üheselt, näed seda Tunniplaani lehel „Vali õpetaja“ all. Vali õige õpetaja või
        vajuta <b>Jäta vahele</b> (näiteks õpetaja abi, keda tunniplaanis pole). Uue graafiku korral lae Excel uuesti üles.
      </Tip>
    </Card>
  )
}
```
(Check `Legend`'s actual props in the file and adapt; reuse `Card` the way `GoodToKnow` does.)

- [ ] **Step 2:** `GuidePage`: render `<DutyHelp />` after `<TableHelp />`. `HelpButton.helpFor`: `/tunniplaan` → `<><TimetableHelp /><DutyHelp /></>`.
- [ ] **Step 3:** `npx tsc -b && npm test -- --run` → PASS; look at /opetus and the Tunniplaan help panel in the browser.
- [ ] **Step 4: Commit** — `docs: Explain break duties in the guide` (`Changelog: Feature`).

---

### Task 10: Final verification

- [ ] `cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw verify` → all tests pass.
- [ ] `cd web && npx tsc -b && npm test -- --run && npm run build` → pass.
- [ ] Browser end-to-end on a Friday with the 03.–04.09 timetable: upload the school's Excel from `~/Downloads`, resolve or skip each "Vali õpetaja" row, mark a teacher with a Friday ÕVT absent, open period 3, check badges and sort, copy the table and check the duty row's text.
- [ ] `git status` clean; the full Excel is not in the repo (`git ls-files | grep -i xlsx` prints nothing) and no name or phone number from it in any committed file.
