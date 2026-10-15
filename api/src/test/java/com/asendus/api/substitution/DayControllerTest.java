package com.asendus.api.substitution;

import com.asendus.api.IntegrationTest;
import com.asendus.api.TestPdfs;
import com.asendus.api.timetable.TeacherKey;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.text.Collator;
import java.util.*;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DayControllerTest extends IntegrationTest {

    static final String FRI = "2026-09-04";
    static final Collator ET = Collator.getInstance(Locale.of("et"));
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, absence, duty_version CASCADE");
        String body = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", TestPdfs.load("opetajad-b.pdf")))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/timetables/{id}/activate", id(body, "$.id")).header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    ResultActions postAbsence(String date, String json) throws Exception {
        return mockMvc.perform(post("/days/{d}/absences", date).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Marks the teacher absent on Friday (whole day if from/to are null) and returns the AbsenceDto JSON. */
    String absent(String key, Integer from, Integer to) throws Exception {
        String range = (from != null ? ",\"fromPeriod\":" + from : "") + (to != null ? ",\"toPeriod\":" + to : "");
        return postAbsence(FRI, "{\"teacherKey\":\"" + key + "\"" + range + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    ResultActions putSubstitution(long rowId, String json) throws Exception {
        return mockMvc.perform(put("/substitutions/{id}", rowId).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions candidates(int period) throws Exception {
        return mockMvc.perform(get("/days/{d}/candidates", FRI).param("period", String.valueOf(period))
                .header("Authorization", bearer())).andExpect(status().isOk());
    }

    String candidatesJson(int period) throws Exception {
        return candidates(period).andReturn().getResponse().getContentAsString();
    }

    static String byKey(String key) {
        return "$[?(@.key=='" + key + "')]";
    }

    /** Keys of active-version teachers whose Friday lesson periods match the predicate, in key order. */
    List<String> fridayTeachers(Predicate<Set<Integer>> match) {
        Map<String, Set<Integer>> periods = new TreeMap<>();
        jdbc.query("""
                select t.key, l.period from teacher t
                join timetable_version v on v.id = t.version_id and v.active
                left join lesson l on l.teacher_id = t.id and l.weekday = 5
                """, rs -> {
            var set = periods.computeIfAbsent(rs.getString(1), k -> new TreeSet<>());
            int p = rs.getInt(2);
            if (!rs.wasNull()) set.add(p);
        });
        return periods.entrySet().stream().filter(e -> match.test(e.getValue())).map(Map.Entry::getKey).toList();
    }

    String first(Predicate<Set<Integer>> match) {
        List<String> keys = fridayTeachers(match);
        assertThat(keys).as("a teacher in the sample timetable matching the predicate").isNotEmpty();
        return keys.getFirst();
    }

    String displayName(String key) {
        return TeacherKey.displayName(jdbc.queryForObject(
                "select t.name from teacher t join timetable_version v on v.id = t.version_id and v.active where t.key = ?",
                String.class, key));
    }

    // ---------- absences ----------

    @Test
    void absenceCreatesOneRowPerLessonThatDay() throws Exception {
        // Pärnpuu Taimi on Friday: periods 1 (4R Ek), 2 (5D Lo), 3 (4D Lo), 4 (6R Aj), 6 (4R Ek)
        postAbsence(FRI, "{\"teacherKey\":\"  Pärnpuu   Taimi \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherKey").value("pärnpuu taimi"))
                .andExpect(jsonPath("$.teacherName").value("Taimi Pärnpuu"))
                .andExpect(jsonPath("$.fromPeriod").value(1))
                .andExpect(jsonPath("$.toPeriod").value(7))
                .andExpect(jsonPath("$.rows[*].period", contains(1, 2, 3, 4, 6)))
                .andExpect(jsonPath("$.rows[0].classes").value("4R"))
                .andExpect(jsonPath("$.rows[0].subject").value("Ek"))
                .andExpect(jsonPath("$.rows[0].substituteKeys").isEmpty())
                .andExpect(jsonPath("$.rows[0].substituteNames").value(""))
                .andExpect(jsonPath("$.rows[0].freeText").value(""));
    }

    @Test
    void partialAbsenceOnlyCoversItsPeriods() throws Exception {
        postAbsence(FRI, "{\"teacherKey\":\"pärnpuu taimi\",\"fromPeriod\":1,\"toPeriod\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rows[*].period", contains(1, 2)));
        postAbsence("2026-09-03", "{\"teacherKey\":\"pärnpuu taimi\",\"fromPeriod\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromPeriod").value(3))
                .andExpect(jsonPath("$.toPeriod").value(7))
                .andExpect(jsonPath("$.rows[*].period", everyItem(greaterThanOrEqualTo(3))));
    }

    @Test
    void weekendAbsenceIsRejected() throws Exception {
        postAbsence("2026-09-05", "{\"teacherKey\":\"pärnpuu taimi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nädalavahetusel tunde pole"));
    }

    @Test
    void duplicateAbsenceIsRejected() throws Exception {
        absent("pärnpuu taimi", null, null);
        postAbsence(FRI, "{\"teacherKey\":\"pärnpuu taimi\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("See õpetaja on juba puudujaks märgitud"));
    }

    @Test
    void unknownTeacherIs404() throws Exception {
        postAbsence(FRI, "{\"teacherKey\":\"keegi muu\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Õpetajat ei leitud"));
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        postAbsence(FRI, "{\"teacherKey\":\"pärnpuu taimi\",\"fromPeriod\":4,\"toPeriod\":2}")
                .andExpect(status().isBadRequest());
        postAbsence(FRI, "{\"teacherKey\":\"pärnpuu taimi\",\"fromPeriod\":0}")
                .andExpect(status().isBadRequest());
        postAbsence(FRI, "{\"teacherKey\":\"pärnpuu taimi\",\"toPeriod\":8}")
                .andExpect(status().isBadRequest());
        postAbsence(FRI, "{\"teacherKey\":\" \"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void absenceNeedsAnActiveTimetable() throws Exception {
        jdbc.execute("TRUNCATE timetable_version CASCADE");
        postAbsence(FRI, "{\"teacherKey\":\"pärnpuu taimi\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Lae esmalt tunniplaan üles"));
    }

    @Test
    void deletingAnUnknownAbsenceIs404() throws Exception {
        mockMvc.perform(delete("/absences/{id}", 999999).header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Puudujat ei leitud"));
    }

    @Test
    void deletingAnAbsenceRemovesItsRows() throws Exception {
        long id = id(absent("pärnpuu taimi", null, null), "$.id");
        mockMvc.perform(delete("/absences/{id}", id).header("Authorization", bearer())).andExpect(status().isNoContent());
        mockMvc.perform(get("/days/{d}", FRI).header("Authorization", bearer()))
                .andExpect(jsonPath("$.absences").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from substitution where absence_id = ?", Long.class, id)).isZero();
    }

    // ---------- day and history ----------

    @Test
    void dayListsAbsencesByNameAndHistoryListsDates() throws Exception {
        List<String> teachers = fridayTeachers(ps -> !ps.isEmpty());
        String a = teachers.get(0), b = teachers.get(1);
        absent(b, null, null);
        absent(a, null, null);
        postAbsence("2026-09-01", "{\"teacherKey\":\"" + a + "\"}").andExpect(status().isCreated());

        List<String> expected = new ArrayList<>(List.of(displayName(a), displayName(b)));
        expected.sort(ET);
        mockMvc.perform(get("/days/{d}", FRI).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(FRI))
                .andExpect(jsonPath("$.weekday").value(5))
                .andExpect(jsonPath("$.absences[*].teacherName", contains(expected.toArray())))
                .andExpect(jsonPath("$.absences[0].rows[*].period", not(empty())));
        mockMvc.perform(get("/days").header("Authorization", bearer()))
                .andExpect(jsonPath("$", contains(FRI, "2026-09-01")));
    }

    @Test
    void weekendDayIsEmpty() throws Exception {
        mockMvc.perform(get("/days/{d}", "2026-09-06").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekday").value(7))
                .andExpect(jsonPath("$.absences").isEmpty());
    }

    // ---------- candidates ----------

    @Test
    void candidatesFollowTheRules() throws Exception {
        // Period 3 on Friday
        String gap = first(ps -> !ps.isEmpty() && !ps.contains(3) && Collections.min(ps) < 3 && Collections.max(ps) > 3);
        String rightAfter = first(ps -> !ps.isEmpty() && Collections.max(ps) == 2);
        String rightBefore = first(ps -> !ps.isEmpty() && Collections.min(ps) == 4);
        String teaching = first(ps -> ps.contains(3));
        String offDay = first(Set::isEmpty);

        String body = candidates(3)
                .andExpect(jsonPath(byKey(gap) + ".status", contains("FREE")))
                .andExpect(jsonPath(byKey(gap) + ".name", contains(displayName(gap))))
                .andExpect(jsonPath(byKey(gap) + ".todayCount", contains(0)))
                .andExpect(jsonPath(byKey(rightAfter) + ".status", contains("FREE")))
                .andExpect(jsonPath(byKey(rightBefore) + ".status", contains("ASK_FIRST")))
                .andExpect(jsonPath(byKey(teaching)).isEmpty())
                .andExpect(jsonPath(byKey(offDay)).isEmpty())
                .andReturn().getResponse().getContentAsString();

        List<String> statuses = JsonPath.read(body, "$[*].status");
        assertThat(statuses).containsOnly("FREE", "ASK_FIRST");
        assertThat(statuses).isSortedAccordingTo(Comparator.comparing(Availability::valueOf));

        // Two or more periods before the first lesson is too early: at period 2, everyone starting at 4 or later
        List<String> tooEarly = fridayTeachers(ps -> !ps.isEmpty() && Collections.min(ps) >= 4);
        assertThat(tooEarly).isNotEmpty();
        List<String> returned2 = JsonPath.read(candidatesJson(2), "$[*].key");
        assertThat(returned2).doesNotContainAnyElementsOf(tooEarly);

        // Two periods after the last lesson is too late (no one ends at 1, so check period 5 against last == 3)
        String twoAfter = first(ps -> !ps.isEmpty() && Collections.max(ps) == 3);
        String rightAfter4 = first(ps -> !ps.isEmpty() && Collections.max(ps) == 4);
        candidates(5)
                .andExpect(jsonPath(byKey(twoAfter)).isEmpty())
                .andExpect(jsonPath(byKey(rightAfter4) + ".status", contains("FREE")));
    }

    @Test
    void candidatesListEveryEligibleTeacherExactly() throws Exception {
        // Recompute the rule independently from the database and compare the full key set for every period
        for (int p = 1; p <= 7; p++) {
            int period = p;
            Set<String> expected = new TreeSet<>(fridayTeachers(ps -> Eligibility.of(ps, period, false) != Availability.HIDDEN));
            List<String> keys = JsonPath.read(candidatesJson(p), "$[*].key");
            assertThat(new TreeSet<>(keys)).as("period " + p).isEqualTo(expected);
            assertThat(keys).as("no duplicates in period " + p).doesNotHaveDuplicates();
        }
    }

    @Test
    void candidateSubjectsCoverTheWholeWeek() throws Exception {
        String gap = first(ps -> !ps.isEmpty() && !ps.contains(3) && Collections.min(ps) < 3 && Collections.max(ps) > 3);
        List<String> weekSubjects = jdbc.queryForList("""
                select distinct l.subject from lesson l join teacher t on t.id = l.teacher_id
                join timetable_version v on v.id = t.version_id and v.active
                where t.key = ? and l.subject <> '' order by l.subject
                """, String.class, gap);
        List<List<String>> subjects = JsonPath.read(candidatesJson(3), byKey(gap) + ".subjects");
        assertThat(subjects).hasSize(1);
        assertThat(subjects.getFirst()).containsExactlyElementsOf(new TreeSet<>(weekSubjects));
    }

    @Test
    void candidatesAreSortedByStatusThenTodayCountThenName() throws Exception {
        // Give one FREE-at-3 teacher a substitution in another period so their todayCount is 1
        List<String> free3 = fridayTeachers(ps -> !ps.isEmpty() && !ps.contains(3) && !ps.contains(5)
                && Collections.min(ps) < 3 && Collections.max(ps) > 5);
        String busy = free3.getFirst();
        String absentKey = fridayTeachers(ps -> ps.contains(5)).stream().filter(k -> !k.equals(busy)).findFirst().orElseThrow();
        long rowId = id(absent(absentKey, 5, 5), "$.rows[0].id");
        putSubstitution(rowId, "{\"substituteKeys\":[\"" + busy + "\"],\"freeText\":\"\"}").andExpect(status().isOk());

        String body = candidatesJson(3);
        List<Map<String, Object>> list = JsonPath.read(body, "$");
        Comparator<Map<String, Object>> order = Comparator
                .comparing((Map<String, Object> c) -> Availability.valueOf((String) c.get("status")))
                .thenComparingInt(c -> ((Number) c.get("todayCount")).intValue())
                .thenComparing(c -> (String) c.get("name"), ET);
        assertThat(list).isSortedAccordingTo(order);
        assertThat(JsonPath.<List<Integer>>read(body, byKey(busy) + ".todayCount")).containsExactly(1);
        // busy is the last FREE entry since everyone else FREE has todayCount 0
        List<String> freeKeys = JsonPath.read(body, "$[?(@.status=='FREE')].key");
        assertThat(freeKeys.getLast()).isEqualTo(busy);
    }

    @Test
    void assignedSubstituteDropsOutOfThatPeriodAndCountsToday() throws Exception {
        // sub has Friday lessons before 4 and after 5 but none in 4–5 → FREE in both
        String sub = first(ps -> !ps.isEmpty() && !ps.contains(4) && !ps.contains(5)
                && Collections.min(ps) < 4 && Collections.max(ps) > 5);
        String absentKey = fridayTeachers(ps -> ps.contains(4)).stream().filter(k -> !k.equals(sub)).findFirst().orElseThrow();

        candidates(4).andExpect(jsonPath(byKey(sub) + ".status", contains("FREE")));

        long rowId = id(absent(absentKey, 4, 4), "$.rows[0].id");
        putSubstitution(rowId, "{\"substituteKeys\":[\"" + sub + "\"],\"freeText\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rowId))
                .andExpect(jsonPath("$.substituteKeys", contains(sub)))
                .andExpect(jsonPath("$.substituteNames").value(displayName(sub)));

        candidates(4).andExpect(jsonPath(byKey(sub)).isEmpty());
        candidates(5)
                .andExpect(jsonPath(byKey(sub) + ".status", contains("FREE")))
                .andExpect(jsonPath(byKey(sub) + ".todayCount", contains(1)));
    }

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

        // She takes someone's period-5 lesson → slot 5 is SUBSTITUTING, and taking period 7 too makes 1..7 in a row
        String other = first(ps -> ps.contains(5));
        String absence = absent(other, 5, 5);
        putSubstitution(id(absence, "$.rows[0].id"), "{\"substituteKeys\":[\"" + taimi + "\"],\"freeText\":\"\"}")
                .andExpect(status().isOk());
        candidates(7)
                .andExpect(jsonPath(byKey(taimi) + ".day[4].kind", contains("SUBSTITUTING")))
                .andExpect(jsonPath(byKey(taimi) + ".streak", contains(7)));
    }

    @Test
    void absentTeacherIsNotACandidateDuringTheirAbsence() throws Exception {
        String x = first(ps -> !ps.isEmpty() && !ps.contains(4) && Collections.min(ps) < 4 && Collections.max(ps) > 4);

        absent(x, 1, 2);
        candidates(4).andExpect(jsonPath(byKey(x) + ".status", contains("FREE")));

        jdbc.execute("TRUNCATE absence CASCADE");
        absent(x, 3, 7);
        candidates(4).andExpect(jsonPath(byKey(x)).isEmpty());
    }

    @Test
    void placeholdersAreNeverCandidates() throws Exception {
        String gap = first(ps -> !ps.isEmpty() && !ps.contains(3) && Collections.min(ps) < 3 && Collections.max(ps) > 3);
        candidates(3).andExpect(jsonPath(byKey(gap) + ".status", contains("FREE")));

        mockMvc.perform(put("/teachers/{key}/placeholder", gap).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeholder\":true}"))
                .andExpect(status().isNoContent());
        for (int p = 1; p <= 7; p++) {
            candidates(p).andExpect(jsonPath(byKey(gap)).isEmpty());
        }
    }

    @Test
    void candidatesRejectWeekendsAndBadPeriods() throws Exception {
        mockMvc.perform(get("/days/{d}/candidates", "2026-09-05").param("period", "3").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nädalavahetusel tunde pole"));
        mockMvc.perform(get("/days/{d}/candidates", FRI).param("period", "8").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    // ---------- substitutions ----------

    @Test
    void multipleSubstitutesAndFreeTextAreStoredTogether() throws Exception {
        List<String> two = fridayTeachers(ps -> !ps.isEmpty()).subList(0, 2);
        long rowId = id(absent("pärnpuu taimi", null, null), "$.rows[0].id");

        putSubstitution(rowId, "{\"substituteKeys\":[\"" + two.get(1) + "\",\"" + two.get(0) + "\"],\"freeText\":\"  Koju \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(1))
                .andExpect(jsonPath("$.classes").value("4R"))
                .andExpect(jsonPath("$.substituteKeys", contains(two.get(1), two.get(0))))
                .andExpect(jsonPath("$.substituteNames").value(displayName(two.get(1)) + " / " + displayName(two.get(0))))
                .andExpect(jsonPath("$.freeText").value("Koju"));

        mockMvc.perform(get("/days/{d}", FRI).header("Authorization", bearer()))
                .andExpect(jsonPath("$.absences[0].rows[0].substituteKeys", contains(two.get(1), two.get(0))))
                .andExpect(jsonPath("$.absences[0].rows[0].freeText").value("Koju"));

        // Clearing works too
        putSubstitution(rowId, "{\"substituteKeys\":[],\"freeText\":\"\"}")
                .andExpect(jsonPath("$.substituteKeys").isEmpty())
                .andExpect(jsonPath("$.substituteNames").value(""))
                .andExpect(jsonPath("$.freeText").value(""));
    }

    @Test
    void unknownSubstituteIsRejected() throws Exception {
        long rowId = id(absent("pärnpuu taimi", null, null), "$.rows[0].id");
        putSubstitution(rowId, "{\"substituteKeys\":[\"keegi muu\"],\"freeText\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tundmatu õpetaja"));
    }

    @Test
    void unknownSubstitutionIs404() throws Exception {
        putSubstitution(999999, "{\"substituteKeys\":[],\"freeText\":\"Koju\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/days")).andExpect(status().isUnauthorized());
    }

    // ---------- review fixes ----------

    @Test
    void malformedPathsAndParamsAre400() throws Exception {
        mockMvc.perform(get("/days/{d}", "not-a-date").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Vigane päring"));
        mockMvc.perform(get("/days/{d}/candidates", FRI).param("period", "abc").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Vigane päring"));
        putSubstitution0("abc", "{\"substituteKeys\":[],\"freeText\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Vigane päring"));
    }

    ResultActions putSubstitution0(String rawId, String json) throws Exception {
        return mockMvc.perform(put("/substitutions/" + rawId).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void aTakenSubstitutionDoesNotExtendTheWorkingSpan() throws Exception {
        // last Friday lesson is 3 → FREE in 4 (last + 1); taking 4 must not make them FREE in 5
        String sub = first(ps -> !ps.isEmpty() && Collections.max(ps) == 3);
        String absentKey = fridayTeachers(ps -> ps.contains(4)).stream().filter(k -> !k.equals(sub)).findFirst().orElseThrow();
        candidates(4).andExpect(jsonPath(byKey(sub) + ".status", contains("FREE")));
        candidates(5).andExpect(jsonPath(byKey(sub)).isEmpty());

        long rowId = id(absent(absentKey, 4, 4), "$.rows[0].id");
        putSubstitution(rowId, "{\"substituteKeys\":[\"" + sub + "\"],\"freeText\":\"\"}").andExpect(status().isOk());

        candidates(5).andExpect(jsonPath(byKey(sub)).isEmpty());
    }

    @Test
    void aSplitCellGivesTwoRowsForTheSamePeriod() throws Exception {
        // Ambos Algi, Thursday period 5: 6M and 6R
        postAbsence("2026-09-03", "{\"teacherKey\":\"ambos algi\",\"fromPeriod\":5,\"toPeriod\":5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[*].period", contains(5, 5)))
                .andExpect(jsonPath("$.rows[*].classes", containsInAnyOrder("6M", "6R")));
    }

    @Test
    void rowsAreASnapshotThatSurvivesANewTimetable() throws Exception {
        absent("pärnpuu taimi", null, null);
        String before = mockMvc.perform(get("/days/{d}", FRI).header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        Object rowsBefore = JsonPath.read(before, "$.absences[0].rows");

        String upload = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "0304.pdf", "application/pdf", TestPdfs.load("opetajad-0304.pdf")))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/timetables/{id}/activate", id(upload, "$.id")).header("Authorization", bearer()))
                .andExpect(status().isOk());

        // Even if the stored timetable changes underneath, the rows keep their copies
        jdbc.update("update lesson set classes = 'ZZ', subject = 'ZZ'");

        String after = mockMvc.perform(get("/days/{d}", FRI).header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(after, "$.absences[0].rows")).isEqualTo(rowsBefore);
        assertThat((Object) JsonPath.read(after, "$.absences[0].teacherName")).isEqualTo("Taimi Pärnpuu");
    }

    @Test
    void keysAlreadyOnTheRowSurviveANewTimetable() throws Exception {
        // Lea Teesalu is in opetajad-b but not in opetajad-0304
        String other = fridayTeachers(ps -> !ps.isEmpty()).getFirst();
        long rowId = id(absent("pärnpuu taimi", null, null), "$.rows[0].id");
        putSubstitution(rowId, "{\"substituteKeys\":[\"teesalu lea\"],\"freeText\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.substituteNames").value("Lea Teesalu"));

        String upload = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "0304.pdf", "application/pdf", TestPdfs.load("opetajad-0304.pdf")))
                        .header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/timetables/{id}/activate", id(upload, "$.id")).header("Authorization", bearer()))
                .andExpect(status().isOk());
        String otherName = displayName(other);

        putSubstitution(rowId, "{\"substituteKeys\":[\"teesalu lea\",\"" + other + "\"],\"freeText\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.substituteKeys", contains("teesalu lea", other)))
                .andExpect(jsonPath("$.substituteNames").value("Lea Teesalu / " + otherName));

        // A key that's neither on the row nor in the active version is still rejected
        putSubstitution(rowId, "{\"substituteKeys\":[\"keegi muu\"],\"freeText\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tundmatu õpetaja"));
    }

    @Test
    void substitutionSizeLimits() throws Exception {
        long rowId = id(absent("pärnpuu taimi", null, null), "$.rows[0].id");
        String elevenKeys = String.join(",", Collections.nCopies(11, "\"" + "ambos algi" + "\""));
        putSubstitution(rowId, "{\"substituteKeys\":[" + elevenKeys + "],\"freeText\":\"\"}")
                .andExpect(status().isBadRequest());
        putSubstitution(rowId, "{\"substituteKeys\":[],\"freeText\":\"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
        putSubstitution(rowId, "{\"substituteKeys\":[],\"freeText\":\"" + "x".repeat(200) + "\"}")
                .andExpect(status().isOk());
    }

    // ---------- parallel teachers ----------

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
                  and ta.key not in (select teacher_key from placeholder_flag)
                  and tb.key not in (select teacher_key from placeholder_flag)
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
}
