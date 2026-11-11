package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.AccessTokenService;
import dev.obrenoxs.ticketing.auth.internal.AuthenticatedAccount;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.time.Instant;
import java.util.UUID;

@AutoConfigureMockMvc
class ProtectedRouteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccessTokenService tokens;

    @Autowired
    JwtEncoder encoder;

    private String validToken(UUID id, Role role) {
        return tokens.issue(new AuthenticatedAccount(id, role)).value();
    }

    @Test
    void shouldReturnAccountForValidToken() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + validToken(id, Role.ORGANIZER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(id.toString()))
                .andExpect(jsonPath("$.role").value("ORGANIZER"));
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForGarbageToken() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForTokenWithAlteredSignature() throws Exception {
        String[] parts = validToken(UUID.randomUUID(), Role.BUYER).split("\\.");
        String signature = parts[2];
        char first = signature.charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + first + signature.substring(1);

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForExpiredToken() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "BUYER")
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(1800))
                .build();
        String expired = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForUnknownRouteWithoutToken() throws Exception {
        mvc.perform(get("/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403ForAuthenticatedUserOnRouteNobodyMayUse() throws Exception {
        mvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + validToken(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isForbidden());
    }
}
