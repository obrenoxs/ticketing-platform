package dev.obrenoxs.ticketing.auth.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("acc-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void shouldTurnRoleClaimIntoSpringRoleAuthority() {
        var authentication = config.jwtAuthenticationConverter().convert(jwt().claim("role", "ADMIN").build());

        assertThat(roles(authentication)).containsExactly("ROLE_ADMIN");
    }

    @Test
    void shouldGiveNoRoleAuthorityWhenTokenHasNoRole() {
        var authentication = config.jwtAuthenticationConverter().convert(jwt().claim("x", "y").build());

        assertThat(roles(authentication)).isEmpty();
    }

    @Test
    void shouldUseSubjectAsTheAuthenticationName() {
        var authentication = config.jwtAuthenticationConverter().convert(jwt().claim("role", "BUYER").build());

        assertThat(authentication.getName()).isEqualTo("acc-1");
    }

    private static List<String> roles(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .toList();
    }

    @Test
    void shouldIgnoreBearerHeaderOnRoutesThatDoNotNeedIt() {
        BearerTokenResolver resolver = config.bearerTokenResolver();
        for (String path : List.of("/auth/login", "/auth/signup", "/auth/refresh",
                "/auth/logout", "/auth/verify-email")) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.addHeader("Authorization", "Bearer expired.token.here");

            assertThat(resolver.resolve(request)).as(path).isNull();
        }
    }

    @Test
    void shouldStillReadBearerHeaderOnProtectedRoutes() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer abc.def.ghi");

        assertThat(config.bearerTokenResolver().resolve(request)).isEqualTo("abc.def.ghi");
    }
}