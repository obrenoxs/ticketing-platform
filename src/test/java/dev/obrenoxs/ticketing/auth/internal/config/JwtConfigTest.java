package dev.obrenoxs.ticketing.auth.internal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.obrenoxs.ticketing.auth.internal.AuthSecrets;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    private static String base64OfBytesFilledWith(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static AuthSecrets secretsWithJwtKey(int jwtKeyFill) {
        return new AuthSecrets(
                base64OfBytesFilledWith(1),
                base64OfBytesFilledWith(2),
                base64OfBytesFilledWith(jwtKeyFill));
    }

    private static String sign(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("acc-1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String validToken(JwtEncoder encoder) {
        Instant now = Instant.now();
        return sign(encoder, now, now.plusSeconds(60));
    }

    @Test
    void shouldVerifyTokenSignedWithTheSameKey() {
        AuthSecrets secrets = secretsWithJwtKey(7);
        JwtEncoder encoder = config.jwtEncoder(secrets);
        JwtDecoder decoder = config.jwtDecoder(secrets);

        assertThat(decoder.decode(validToken(encoder)).getSubject()).isEqualTo("acc-1");
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtEncoder attackerEncoder = config.jwtEncoder(secretsWithJwtKey(7));
        JwtDecoder ourDecoder = config.jwtDecoder(secretsWithJwtKey(9));

        assertThatThrownBy(() -> ourDecoder.decode(validToken(attackerEncoder)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTokenWhosePayloadWasTamperedWith() {
        AuthSecrets secrets = secretsWithJwtKey(7);
        String token = validToken(config.jwtEncoder(secrets));
        JwtDecoder decoder = config.jwtDecoder(secrets);

        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("acc-1", "acc-2").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forged + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        AuthSecrets secrets = secretsWithJwtKey(7);
        Instant now = Instant.now();
        String expired = sign(config.jwtEncoder(secrets), now.minusSeconds(600), now.minusSeconds(300));

        assertThatThrownBy(() -> config.jwtDecoder(secrets).decode(expired))
                .isInstanceOf(JwtException.class);
    }
}