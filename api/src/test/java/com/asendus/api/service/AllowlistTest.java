package com.asendus.api.service;

import com.asendus.api.IntegrationTest;
import com.asendus.api.service.GoogleTokenVerifier.GoogleIdentity;
import com.asendus.api.model.AppUser;
import com.asendus.api.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AllowlistTest extends IntegrationTest {

    @MockitoBean GoogleTokenVerifier googleTokenVerifier;
    @Autowired AppUserRepository userRepository;
    @Autowired TokenService tokenService;

    @Test
    void login_rejectsEmailsNotOnTheAllowlist() throws Exception {
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("t")).thenReturn(new GoogleIdentity(sub, "stranger@example.com", "S"));

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Sul pole ligipääsu, küsi Tomilt"));
        // The user row created during login is rolled back
        assertThat(userRepository.findByGoogleSub(sub)).isEmpty();
    }

    @Test
    void existingTokens_areRejectedOnceTheEmailIsNotAllowlisted() throws Exception {
        AppUser user = userRepository.save(AppUser.builder()
                .googleSub(UUID.randomUUID().toString()).email("removed@example.com").name("R").build());
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + tokenService.generateToken(user.getId(), user.getEmail())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingTokens_matchEmailsCaseInsensitively() throws Exception {
        AppUser user = userRepository.save(AppUser.builder()
                .googleSub(UUID.randomUUID().toString()).email("USER@Example.com").name("U").build());
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + tokenService.generateToken(user.getId(), user.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void login_matchesEmailsCaseInsensitively() throws Exception {
        when(googleTokenVerifier.verify("t2")).thenReturn(new GoogleIdentity(UUID.randomUUID().toString(), "allowed@example.COM", "A"));

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t2\"}"))
                .andExpect(status().isOk());
    }
}
