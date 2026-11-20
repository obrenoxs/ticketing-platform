package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@AutoConfigureMockMvc
class RouteAccessMatrixIntegrationTest extends AbstractIntegrationTest {

    enum Who { ANON, BUYER, ORGANIZER, ADMIN }

    enum Expect { ALLOWED, UNAUTHORIZED, FORBIDDEN }

    @Autowired
    MockMvc mvc;

    private static Stream<Arguments> route(String method, String path,
                                           Expect anon, Expect buyer, Expect organizer, Expect admin) {
        return Stream.of(
                Arguments.of(method, path, Who.ANON, anon),
                Arguments.of(method, path, Who.BUYER, buyer),
                Arguments.of(method, path, Who.ORGANIZER, organizer),
                Arguments.of(method, path, Who.ADMIN, admin));
    }

    static Stream<Arguments> matrix() {
        Expect ok = Expect.ALLOWED;
        Expect u = Expect.UNAUTHORIZED;
        Expect f = Expect.FORBIDDEN;
        String someId = UUID.randomUUID().toString();

        return Stream.of(
                route("POST", "/auth/signup", ok, ok, ok, ok),
                route("POST", "/auth/login", ok, ok, ok, ok),
                route("POST", "/auth/refresh", ok, ok, ok, ok),
                route("POST", "/auth/logout", ok, ok, ok, ok),
                route("GET", "/auth/verify-email", ok, ok, ok, ok),
                route("GET", "/actuator/health", ok, ok, ok, ok),
                route("GET", "/auth/me", u, ok, ok, ok),
                route("PUT", "/auth/me", u, f, f, f),
                // area administrativa
                route("GET", "/admin/organizers/pending", u, f, f, ok),
                route("POST", "/admin/organizers/" + someId + "/approve", u, f, f, ok),
                route("POST", "/admin/organizers/" + someId + "/reject", u, f, f, ok),
                route("GET", "/admin/anything", u, f, f, ok),
                // tudo o que ninguem declarou
                route("GET", "/does-not-exist", u, f, f, f),
                route("DELETE", "/auth/signup", u, f, f, f)
        ).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @MethodSource("matrix")
    void everyRouteShouldBeAccessibleExactlyAsDesigned(String method, String path, Who who, Expect expected)
            throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (who != Who.ANON) {
            request = request.with(jwt()
                    .jwt(j -> j.subject(UUID.randomUUID().toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + who.name())));
        }

        int status = mvc.perform(request).andReturn().getResponse().getStatus();

        switch (expected) {
            case ALLOWED -> assertThat(status).isNotIn(401, 403);
            case UNAUTHORIZED -> assertThat(status).isEqualTo(401);
            case FORBIDDEN -> assertThat(status).isEqualTo(403);
        }
    }
}