package com.asendus.api.duty;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    void uploadTimetable() throws Exception { String body = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", TestPdfs.load("opetajad-b.pdf")))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(post("/timetables/{id}/activate", id).header("Authorization", bearer()))
                .andExpect(status().isOk()); }

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
