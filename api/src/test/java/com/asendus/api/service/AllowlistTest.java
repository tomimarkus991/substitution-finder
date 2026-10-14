package com.asendus.api.service;

import com.asendus.api.IntegrationTest;
import com.asendus.api.service.GoogleTokenVerifier.GoogleIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AllowlistTest extends IntegrationTest {

    @MockitoBean GoogleTokenVerifier googleTokenVerifier;

    @Test
    void login_rejectsEmailsNotOnTheAllowlist() throws Exception {
        when(googleTokenVerifier.verify("t")).thenReturn(new GoogleIdentity(UUID.randomUUID().toString(), "stranger@example.com", "S"));

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Sul pole ligipääsu, küsi Tomilt"));
    }

    @Test
    void login_matchesEmailsCaseInsensitively() throws Exception {
        when(googleTokenVerifier.verify("t2")).thenReturn(new GoogleIdentity(UUID.randomUUID().toString(), "allowed@example.COM", "A"));

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t2\"}"))
                .andExpect(status().isOk());
    }
}
