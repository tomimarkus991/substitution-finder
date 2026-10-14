package com.asendus.api.substitution;

import com.asendus.api.IntegrationTest;
import com.asendus.api.TestDutySheet;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("unchecked")
class DutyDayTest extends IntegrationTest {
    static final String FRI = "2026-09-04";
    @Autowired JdbcTemplate jdbc;

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

    ResultActions postAbsence(String date, String json) throws Exception {
        return mockMvc.perform(post("/days/{d}/absences", date).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String candidatesJson(int period) throws Exception {
        return mockMvc.perform(get("/days/{d}/candidates", FRI).param("period", String.valueOf(period))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
        assertThat((List<String>) me.get("duties")).containsExactly("ÕVT 10:45–11:15");
        List<Map<String, Object>> sameStatus = all.stream().filter(c -> c.get("status").equals(me.get("status"))).toList();
        assertThat(sameStatus.get(sameStatus.size() - 1).get("key")).isEqualTo(onDuty);
        assertThat(all).filteredOn(c -> !c.get("key").equals(onDuty))
                .allSatisfy(c -> assertThat((List<?>) c.get("duties")).isEmpty());
    }

    @Test void unresolvedDutyCountsOnlyAfterItIsResolved() throws Exception {
        assertThat(JsonPath.<List<List<String>>>read(candidatesJson(3), "$[?(@.key=='" + ambiguous + "')].duties").get(0)).isEmpty();
        long id = jdbc.queryForObject("select id from duty where teacher_key is null", Long.class);
        mockMvc.perform(put("/duties/{id}", id).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"teacherKey\":\"" + ambiguous + "\"}"))
                .andExpect(status().isOk());
        assertThat(JsonPath.<List<List<String>>>read(candidatesJson(3), "$[?(@.key=='" + ambiguous + "')].duties").get(0))
                .containsExactly("ÕVT 10:45–11:15");
    }

    @Test void pickingASubstituteForADutyRowBlocksThemOnlyInThatPeriod() throws Exception {
        String body = postAbsence(FRI, "{\"teacherKey\":\"" + onDuty + "\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int rowId = JsonPath.<List<Integer>>read(body, "$.rows[?(@.dutyLabel != null)].id").get(0);
        int dutyPeriod = JsonPath.<List<Integer>>read(body, "$.rows[?(@.dutyLabel != null)].period").get(0);

        String picked = null;
        int otherPeriod = 0;
        for (String key : JsonPath.<List<String>>read(candidatesJson(dutyPeriod), "$[*].key")) {
            for (int p = 1; p <= 7 && picked == null; p++) {
                if (p != dutyPeriod && JsonPath.<List<String>>read(candidatesJson(p), "$[*].key").contains(key)) {
                    picked = key;
                    otherPeriod = p;
                }
            }
            if (picked != null) break;
        }
        assertThat(picked).as("a teacher who is a candidate in two periods").isNotNull();

        mockMvc.perform(put("/substitutions/{id}", rowId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"substituteKeys\":[\"" + picked + "\"],\"freeText\":\"\"}"))
                .andExpect(status().isOk());

        assertThat(JsonPath.<List<String>>read(candidatesJson(dutyPeriod), "$[*].key")).doesNotContain(picked);
        assertThat(JsonPath.<List<String>>read(candidatesJson(otherPeriod), "$[*].key")).contains(picked);
    }
}
