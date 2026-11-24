package com.asendus.api.substitution;

import com.asendus.api.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatsControllerTest extends IntegrationTest {

    static final String FRI = "2026-09-04";
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, teacher_name_override, absence, duty_version CASCADE");
        uploadTimetable();
    }

    // ---------- helpers ----------

    /** Marks the teacher absent all Friday; returns the ids of their lesson rows in period order. */
    List<Long> absentRows(String key) throws Exception {
        return absentRows(key, FRI);
    }

    /** Marks the teacher absent all day on the given date; returns the ids of their lesson rows in period order. */
    List<Long> absentRows(String key, String date) throws Exception {
        return postAbsence(date, "{\"teacherKey\":\"" + key + "\"}");
    }

    /** Marks the teacher absent for the given Friday periods; returns the ids of their lesson rows. */
    List<Long> absentRows(String key, int fromPeriod, int toPeriod) throws Exception {
        return postAbsence(FRI, "{\"teacherKey\":\"" + key + "\",\"fromPeriod\":" + fromPeriod
                + ",\"toPeriod\":" + toPeriod + "}");
    }

    private List<Long> postAbsence(String date, String json) throws Exception {
        String body = mockMvc.perform(post("/days/{d}/absences", date).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(body, "$.rows[*].id").stream().map(Number::longValue).toList();
    }

    void pick(long rowId, String freeText, String... keys) throws Exception {
        String list = String.join(",", java.util.Arrays.stream(keys).map(k -> "\"" + k + "\"").toList());
        mockMvc.perform(put("/substitutions/{id}", rowId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"substituteKeys\":[" + list + "],\"freeText\":\"" + freeText + "\"}"))
                .andExpect(status().isOk());
    }

    ResultActions stats(String query) throws Exception {
        return mockMvc.perform(get("/stats" + query).header("Authorization", bearer()));
    }

    List<Map<String, Object>> statsList() throws Exception {
        return statsList("");
    }

    List<Map<String, Object>> statsList(String query) throws Exception {
        String body = stats(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    Map<String, Object> row(List<Map<String, Object>> list, String key) {
        return list.stream().filter(r -> key.equals(r.get("key"))).findFirst().orElseThrow();
    }

    Map<String, Object> row(String key) throws Exception {
        return row(statsList(), key);
    }

    /** Active teachers in key order, excluding the absent one. */
    List<String> others(String absent) {
        return jdbc.queryForList("""
                select t.key from teacher t join timetable_version v on v.id = t.version_id and v.active
                where t.key <> ? order by t.key""", String.class, absent);
    }

    static final String ABSENT = "pärnpuu taimi"; // five lessons on Friday

    // ---------- tests ----------

    @Test
    void everyActiveTeacherIsListedWithZerosWhenNothingIsSaved() throws Exception {
        int teachers = jdbc.queryForObject(
                "select count(*) from teacher t join timetable_version v on v.id = t.version_id and v.active", Integer.class);
        List<Map<String, Object>> list = statsList();
        assertThat(list).hasSize(teachers);
        assertThat(list).allSatisfy(r -> {
            assertThat(r.get("lessonCovers")).isEqualTo(0);
            assertThat(r.get("dutyCovers")).isEqualTo(0);
            assertThat(r.get("totalCovers")).isEqualTo(0);
            assertThat(r.get("missedLessons")).isEqualTo(0);
        });
        assertThat(row(ABSENT).get("name")).isEqualTo("Taimi Pärnpuu");
    }

    @Test
    void eachRowCountsOnceForEverySubstituteOnIt() throws Exception {
        List<Long> rows = absentRows(ABSENT);
        String a = others(ABSENT).get(0), b = others(ABSENT).get(1);
        pick(rows.get(0), "", a);
        pick(rows.get(1), "", a, b);  // shared: both get a full cover

        assertThat(row(a)).containsEntry("lessonCovers", 2).containsEntry("totalCovers", 2);
        assertThat(row(b)).containsEntry("lessonCovers", 1);
    }

    @Test
    void freeTextOnlyRowCountsForNobody() throws Exception {
        List<Long> rows = absentRows(ABSENT);
        pick(rows.get(0), "Koju");

        assertThat(statsList()).allSatisfy(r -> assertThat(r.get("totalCovers")).isEqualTo(0));
    }

    @Test
    void dutyRowCountsAsDutyCover() throws Exception {
        List<Long> rows = absentRows(ABSENT);
        String a = others(ABSENT).get(0);
        jdbc.update("update substitution set duty_label = 'ÕVT 10:45–11:15 · pos 4' where id = ?", rows.get(0));
        pick(rows.get(0), "", a);
        pick(rows.get(1), "", a);

        assertThat(row(a)).containsEntry("lessonCovers", 1).containsEntry("dutyCovers", 1)
                .containsEntry("totalCovers", 2);
    }

    @Test
    void placeholderTeachersAreLeftOut() throws Exception {
        String a = others(ABSENT).get(0);
        mockMvc.perform(put("/teachers/{k}/placeholder", a).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeholder\":true}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(statsList()).noneMatch(r -> a.equals(r.get("key")));
    }

    @Test
    void coversUnderKeysNotInTheTimetableAreDropped() throws Exception {
        List<Long> rows = absentRows(ABSENT);
        jdbc.update("update substitution set substitute_keys = '{\"lahkunud õpetaja\"}' where id = ?", rows.get(0));

        assertThat(statsList()).noneMatch(r -> "lahkunud õpetaja".equals(r.get("key")));
    }

    @Test
    void fewestLessonCoversComeFirstThenByName() throws Exception {
        List<Long> rows = absentRows(ABSENT);
        String busy = others(ABSENT).get(0);
        pick(rows.get(0), "", busy);

        List<Map<String, Object>> list = statsList();
        assertThat(list.getLast().get("key")).isEqualTo(busy);
        List<String> zeroNames = list.subList(0, list.size() - 1).stream().map(r -> (String) r.get("name")).toList();
        assertThat(zeroNames).isSortedAccordingTo(com.asendus.api.timetable.TeacherKey.DISPLAY_ORDER);
    }

    @Test
    void oneLessonAbsenceIsOneMissedLesson() throws Exception {
        absentRows(ABSENT, 1, 1);

        assertThat(row(ABSENT)).containsEntry("missedLessons", 1);
    }

    @Test
    void fullDayAbsenceMissesTheTeachersLessonsThatWeekday() throws Exception {
        absentRows(ABSENT);

        assertThat(row(ABSENT)).containsEntry("missedLessons", 5);
        assertThat(statsList()).filteredOn(r -> !ABSENT.equals(r.get("key")))
                .allSatisfy(r -> assertThat(r.get("missedLessons")).isEqualTo(0));
    }

    @Test
    void missedDutyIsNotAMissedLesson() throws Exception {
        List<Long> rows = absentRows(ABSENT);
        jdbc.update("update substitution set duty_label = 'ÕVT 10:45–11:15 · pos 4' where id = ?", rows.get(0));

        assertThat(row(ABSENT)).containsEntry("missedLessons", 4);
    }

    @Test
    void noActiveTimetableGivesEmptyList() throws Exception {
        jdbc.execute("TRUNCATE timetable_version CASCADE");
        assertThat(statsList()).isEmpty();
    }

    @Test
    void rangeIsInclusiveAtBothEnds() throws Exception {
        String a = others(ABSENT).get(0);
        // one lesson cover on each of: the day before, from, to, the day after
        for (String date : List.of("2026-09-03", "2026-09-04", "2026-09-07", "2026-09-08")) {
            pick(absentRows(ABSENT, date).getFirst(), "", a);
        }

        assertThat(row(statsList("?from=2026-09-04&to=2026-09-07"), a)).containsEntry("lessonCovers", 2);
        assertThat(row(statsList("?from=2026-09-04"), a)).containsEntry("lessonCovers", 3);
        assertThat(row(statsList("?to=2026-09-07"), a)).containsEntry("lessonCovers", 3);
        assertThat(row(statsList(), a)).containsEntry("lessonCovers", 4);
    }

    @Test
    void futureDatesInTheRangeAreCounted() throws Exception {
        String a = others(ABSENT).get(0);
        pick(absentRows(ABSENT, "2036-09-05").getFirst(), "", a);

        assertThat(row(statsList("?from=2026-09-01"), a)).containsEntry("lessonCovers", 1);
    }

    @Test
    void fromAfterToIsRejected() throws Exception {
        stats("?from=2026-09-08&to=2026-09-07")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Alguskuupäev ei saa olla pärast lõppkuupäeva"));
    }

    @Test
    void missedLessonsFollowTheDateRange() throws Exception {
        absentRows(ABSENT); // Friday 2026-09-04, five lessons

        assertThat(row(statsList("?from=2026-09-04&to=2026-09-04"), ABSENT)).containsEntry("missedLessons", 5);
        assertThat(row(statsList("?from=2026-09-05"), ABSENT)).containsEntry("missedLessons", 0);
        assertThat(row(statsList("?to=2026-09-03"), ABSENT)).containsEntry("missedLessons", 0);
    }
}
