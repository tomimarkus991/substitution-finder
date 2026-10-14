package com.asendus.api.controller;

import com.asendus.api.IntegrationTest;
import com.asendus.api.service.GoogleTokenVerifier;
import com.asendus.api.service.GoogleTokenVerifier.GoogleIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends IntegrationTest {


    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @Test
    void login_createsTheUserAndReturnsAWorkingToken() throws Exception {
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentity(sub, "user@example.com", "User"));

        String body = login("google-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("User"));
    }

    @Test
    void login_updatesEmailAndNameOnTheNextLogin() throws Exception {
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("first")).thenReturn(new GoogleIdentity(sub, "old@example.com", "Old"));
        when(googleTokenVerifier.verify("second")).thenReturn(new GoogleIdentity(sub, "new@example.com", "New"));

        String firstId = login("first").andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        login("second")
                .andExpect(jsonPath("$.user.id").value(Long.parseLong(firstId)))
                .andExpect(jsonPath("$.user.email").value("new@example.com"))
                .andExpect(jsonPath("$.user.name").value("New"));
    }

    @Test
    void login_rejectsAnInvalidGoogleToken() throws Exception {
        when(googleTokenVerifier.verify("forged"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token"));

        login("forged")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid Google token"));
    }

    @Test
    void login_rejectsABlankToken() throws Exception {
        login(" ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("idToken must not be blank"));
    }

    @Test
    void me_returns401WithoutAToken() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void me_returns401ForAGarbageToken() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions login(String idToken) throws Exception {
        return mockMvc.perform(post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + idToken + "\"}"));
    }
}
