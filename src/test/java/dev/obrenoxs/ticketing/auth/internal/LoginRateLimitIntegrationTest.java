package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class LoginRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void shouldThrottleAfterFiveFailuresForTheSameEmail() throws Exception {
        String body = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(UUID.randomUUID() + "@example.com");

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
