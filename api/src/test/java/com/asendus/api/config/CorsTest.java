package com.asendus.api.config;

import com.asendus.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void allowsTheWebOrigin() throws Exception {
        mockMvc.perform(options("/me").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mockMvc.perform(options("/me").header("Origin", "https://evil.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
