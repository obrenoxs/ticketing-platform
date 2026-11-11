package dev.obrenoxs.ticketing.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.obrenoxs.ticketing.auth.internal.config.JwtConfig;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AccessTokenServiceTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final AuthProperties properties = mock(AuthProperties.class);
    private final AccessTokenService service;
    private final JwtDecoder decoder;

    AccessTokenServiceTest() {
        when(properties.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        AuthSecrets secrets = new AuthSecrets(base64Of(1), base64Of(2), base64Of(3));
        JwtConfig config = new JwtConfig();
        this.service = new AccessTokenService(
                config.jwtEncoder(secrets), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        this.decoder = config.jwtDecoder(secrets);
    }

    private static String base64Of(int fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void shouldPutAccountIdAndRoleInTheToken() {
        UUID id = UUID.randomUUID();

        Jwt jwt = decoder.decode(service.issue(new AuthenticatedAccount(id, Role.ORGANIZER)).value());

        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString(AccessTokenService.ROLE_CLAIM)).isEqualTo("ORGANIZER");
    }

    @Test
    void shouldExpireAfterTheConfiguredLifetime() {
        IssuedAccessToken issued = service.issue(new AuthenticatedAccount(UUID.randomUUID(), Role.BUYER));

        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void shouldCarryNothingBesideTheFourExpectedClaims() {
        Jwt jwt = decoder.decode(
                service.issue(new AuthenticatedAccount(UUID.randomUUID(), Role.BUYER)).value());

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "iat", "exp");
    }
}