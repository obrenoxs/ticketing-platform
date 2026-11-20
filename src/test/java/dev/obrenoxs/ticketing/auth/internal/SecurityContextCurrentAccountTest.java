package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.api.AccountRole;
import dev.obrenoxs.ticketing.auth.api.CurrentAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentAccountTest {

    private final SecurityContextCurrentAccount provider = new SecurityContextCurrentAccount();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String subject, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject(subject);
        if (role != null) {
            builder.claim(AccessTokenService.ROLE_CLAIM, role);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    @Test
    void shouldReadAccountIdAndRoleFromTheToken() {
        UUID id = UUID.randomUUID();
        login(id.toString(), "ORGANIZER");

        assertThat(provider.require()).isEqualTo(new CurrentAccount(id, AccountRole.ORGANIZER));
    }

    @Test
    void shouldFailWhenNobodyIsAuthenticated() {
        assertThatThrownBy(provider::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailOnAnUnknownRole() {
        login(UUID.randomUUID().toString(), "SUPERUSER");

        assertThatThrownBy(provider::require).isInstanceOf(IllegalArgumentException.class);
    }
}
