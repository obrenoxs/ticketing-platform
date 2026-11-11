package dev.obrenoxs.ticketing.auth.internal;

import com.jayway.jsonpath.JsonPath;
import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordHasher hasher;

    private static String json(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private Account savedAccount(String email, boolean active) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Account account = Account.register(UUID.randomUUID(), email, "Ana",
                hasher.hash("password-123"), new byte[] {1}, 1,
                UUID.randomUUID().toString().getBytes(), now);
        if (active) {
            account.activate(now);
        }
        return accounts.saveAndFlush(account);
    }

    @Test
    void shouldUseTheTokenReceivedOnLoginToCallAProtectedRoute() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        Account account = savedAccount(email, true);

        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "password-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.role").value("BUYER"));
    }

    @Test
    void shouldGiveIdenticalFailureForWrongPasswordUnknownEmailAndPendingAccount() throws Exception {
        String activeEmail = UUID.randomUUID() + "@example.com";
        String pendingEmail = UUID.randomUUID() + "@example.com";
        savedAccount(activeEmail, true);
        savedAccount(pendingEmail, false);

        String wrongPassword = failureWithoutRequestId(activeEmail, "wrong-password");
        String unknownEmail = failureWithoutRequestId(UUID.randomUUID() + "@example.com", "password-123");
        String pending = failureWithoutRequestId(pendingEmail, "password-123");

        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(pending).isEqualTo(wrongPassword);
    }

    private String failureWithoutRequestId(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("\"requestId\":\"[^\"]*\"", "\"requestId\":\"-\"");
    }
}
