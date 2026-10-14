package com.asendus.api;

import com.asendus.api.model.AppUser;
import com.asendus.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Makes sure the dev user exists. Add realistic sample data for it here as the app grows. */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AuthService authService;

    @Override
    public void run(String... args) {
        AppUser devUser = authService.getOrCreateDevUser();
        log.info("[DataSeeder] Dev user ready: {} (id {}).", devUser.getEmail(), devUser.getId());
    }
}
