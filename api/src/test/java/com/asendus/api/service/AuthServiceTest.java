package com.asendus.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    @Test
    void refusesToStart_whenBypassIsOnOutsideDevProfile() {
        MockEnvironment prod = new MockEnvironment();

        assertThatThrownBy(() -> new AuthService(null, null, null, prod, true, "dev@asendus.app", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the 'dev' profile");
    }

    @Test
    void refusesToStart_whenBypassHasNoDevUserEmail() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatThrownBy(() -> new AuthService(null, null, null, dev, true, " ", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev-user-email");
    }

    @Test
    void starts_withBypassOff() {
        assertThatCode(() -> new AuthService(null, null, null, new MockEnvironment(), false, null, ""))
                .doesNotThrowAnyException();
    }
}
