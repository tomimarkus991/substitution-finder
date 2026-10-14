package com.asendus.api;

import com.asendus.api.model.AppUser;
import com.asendus.api.repository.AppUserRepository;
import com.asendus.api.service.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full app against a Testcontainers Postgres. Subclasses share one context (and one container). */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.auth.google-client-ids=test-client-id",
        "app.auth.allowed-emails=user@example.com, old@example.com,new@example.com,Allowed@Example.com,dev@asendus.app"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private AppUserRepository testUserRepository;

    /** Bearer header for a stored test user (bypasses Google). */
    protected String bearer() {
        AppUser user = testUserRepository.findByGoogleSub("test-user").orElseGet(() -> testUserRepository.save(
                AppUser.builder().googleSub("test-user").email("user@example.com").name("Test").build()));
        return "Bearer " + tokenService.generateToken(user.getId(), user.getEmail());
    }

    /** Uploads the fixture timetable and makes it the active one. */
    protected void uploadTimetable() throws Exception {
        String body = mockMvc.perform(multipart("/timetables")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", TestPdfs.load("opetajad-b.pdf")))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(post("/timetables/{id}/activate", id).header("Authorization", bearer()))
                .andExpect(status().isOk());
    }
}
