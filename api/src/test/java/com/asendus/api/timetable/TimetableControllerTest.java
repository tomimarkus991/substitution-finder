package com.asendus.api.timetable;

import com.asendus.api.IntegrationTest;
import com.asendus.api.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import com.jayway.jsonpath.JsonPath;

import java.text.Collator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimetableControllerTest extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE timetable_version, placeholder_flag, absence CASCADE");
    }

    private long upload(String file) throws Exception {
        String body = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", file, "application/pdf", TestPdfs.load(file)))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void activate(long id) throws Exception {
        mockMvc.perform(post("/timetables/{id}/activate", id).header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void uploadShowsAPreviewWithoutActivating() throws Exception {
        mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", TestPdfs.load("opetajad-b.pdf")))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.fileName").value("b.pdf"))
                .andExpect(jsonPath("$.teacherCount").value(46))
                .andExpect(jsonPath("$.addedTeachers").isEmpty())
                .andExpect(jsonPath("$.removedTeachers").isEmpty());

        mockMvc.perform(get("/timetables/active").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Tunniplaan puudub"));
    }

    @Test
    void activateMakesItTheActiveTimetable() throws Exception {
        long id = upload("opetajad-b.pdf");
        mockMvc.perform(post("/timetables/{id}/activate", id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/timetables/active").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.teachers.length()").value(46))
                .andExpect(jsonPath("$.teachers[?(@.key=='pärnpuu taimi')].displayName").value("Taimi Pärnpuu"));

        mockMvc.perform(get("/timetables/active/teachers/{key}", "pärnpuu taimi").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pärnpuu Taimi"))
                .andExpect(jsonPath("$.lessons[0].weekday").value(1))
                .andExpect(jsonPath("$.lessons[0].period").value(1))
                .andExpect(jsonPath("$.lessons[0].classes").value("4R"));
    }

    @Test
    void unknownTeacherIs404() throws Exception {
        activate(upload("opetajad-b.pdf"));
        mockMvc.perform(get("/timetables/active/teachers/{key}", "keegi muu").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Õpetajat ei leitud"));
    }

    @Test
    void secondUploadDiffsAgainstActive() throws Exception {
        activate(upload("opetajad-b.pdf"));
        mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "0304.pdf", "application/pdf", TestPdfs.load("opetajad-0304.pdf")))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.teacherCount").value(45))
                .andExpect(jsonPath("$.addedTeachers").value(empty()))
                .andExpect(jsonPath("$.removedTeachers").value(contains("Lea Teesalu")));
    }

    @Test
    void secondUploadDropsUnactivatedPreviews() throws Exception {
        activate(upload("opetajad-b.pdf"));
        long stale = upload("opetajad-0304.pdf");
        upload("opetajad-0304.pdf");

        mockMvc.perform(post("/timetables/{id}/activate", stale).header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Tunniplaani ei leitud"));
    }

    @Test
    void activatingANewVersionReplacesTheOldOne() throws Exception {
        activate(upload("opetajad-b.pdf"));
        long next = upload("opetajad-0304.pdf");
        activate(next);

        mockMvc.perform(get("/timetables/active").header("Authorization", bearer()))
                .andExpect(jsonPath("$.id").value(next))
                .andExpect(jsonPath("$.teachers.length()").value(45));
    }

    @Test
    void placeholderFlagSurvivesANewVersion() throws Exception {
        activate(upload("opetajad-b.pdf"));
        mockMvc.perform(put("/teachers/{key}/placeholder", "puutöö").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeholder\":true}"))
                .andExpect(status().isNoContent());
        activate(upload("opetajad-b.pdf"));

        mockMvc.perform(get("/timetables/active/teachers/{key}", "puutöö").header("Authorization", bearer()))
                .andExpect(jsonPath("$.placeholder").value(true));
        mockMvc.perform(get("/timetables/active").header("Authorization", bearer()))
                .andExpect(jsonPath("$.teachers[?(@.key=='puutöö')].placeholder").value(true))
                .andExpect(jsonPath("$.teachers[?(@.key=='pärnpuu taimi')].placeholder").value(false));

        mockMvc.perform(put("/teachers/{key}/placeholder", "puutöö").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeholder\":false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/timetables/active/teachers/{key}", "puutöö").header("Authorization", bearer()))
                .andExpect(jsonPath("$.placeholder").value(false));
    }

    @Test
    void rejectsANonPdf() throws Exception {
        mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "hello".getBytes()))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Faili ei õnnestunud PDF-ina lugeda"));
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Fail puudub"));
    }

    @Test
    void rejectsAMissingFile() throws Exception {
        mockMvc.perform(multipart("/timetables").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Fail puudub"));
    }

    @Test
    void activatingAnUnknownVersionIs404() throws Exception {
        mockMvc.perform(post("/timetables/{id}/activate", 999999).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/timetables/active")).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherListIsInEstonianDisplayNameOrder() throws Exception {
        activate(upload("opetajad-b.pdf"));
        String body = mockMvc.perform(get("/timetables/active").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(body, "$.teachers[*].displayName");
        assertThat(names).hasSize(46).isSortedAccordingTo(Collator.getInstance(Locale.of("et"))::compare);
    }
}
