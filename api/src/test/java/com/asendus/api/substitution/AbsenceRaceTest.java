package com.asendus.api.substitution;

import com.asendus.api.IntegrationTest;
import com.asendus.api.TestPdfs;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A double-tap can get past the duplicate check before either insert commits. Simulate that by making the
 * check always say "no duplicate", so the second insert hits the (date, teacher_key) unique constraint.
 */
class AbsenceRaceTest extends IntegrationTest {

    @MockitoSpyBean AbsenceRepository absenceRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, absence CASCADE");
        String body = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", TestPdfs.load("opetajad-b.pdf")))
                        .header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(post("/timetables/{id}/activate", id).header("Authorization", bearer()))
                .andExpect(status().isOk());
        doReturn(false).when(absenceRepository).existsByAbsenceDateAndTeacherKey(any(), anyString());
    }

    @Test
    void aDuplicateThatSlipsPastTheCheckIs409() throws Exception {
        String json = "{\"teacherKey\":\"pärnpuu taimi\"}";
        mockMvc.perform(post("/days/{d}/absences", "2026-09-04").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/days/{d}/absences", "2026-09-04").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Andmed muutusid samal ajal, proovi uuesti"));
    }
}
