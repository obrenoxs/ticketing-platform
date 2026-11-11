package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.EmailVerificationToken;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EmailVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    EmailVerificationTokenRepository tokens;

    @MockitoBean
    EmailVerificationNotifier notifier;

    private String signupAndGetToken(String email, String cpf) throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"%s","password":"password-123","cpf":"%s"}
                                """.formatted(email, cpf)))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(eq(email), any(), link.capture());
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    @Test
    void shouldActivateAccountWhenLinkIsOpened() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = signupAndGetToken(email, TestCpfs.random());

        mvc.perform(get("/auth/verify-email").param("token", token)).andExpect(status().isOk());

        Account account = accounts.findByEmail(email).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(tokens.findByTokenHash(OpaqueTokens.hash(token)).orElseThrow().getUsedAt()).isNotNull();

        mvc.perform(get("/auth/verify-email").param("token", token)).andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnknownToken() throws Exception {
        mvc.perform(get("/auth/verify-email").param("token", "does-not-exist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Account account = accounts.saveAndFlush(Account.register(UUID.randomUUID(),
                UUID.randomUUID() + "@example.com", "Ana", "hash",
                new byte[] {1}, 1, UUID.randomUUID().toString().getBytes(), now));
        tokens.saveAndFlush(new EmailVerificationToken(UUID.randomUUID(), account.getId(),
                OpaqueTokens.hash("expired-token"), now.minusSeconds(1), now.minusSeconds(3600)));

        mvc.perform(get("/auth/verify-email").param("token", "expired-token"))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.PENDING_EMAIL);
    }

    @Test
    void shouldHandleConcurrentClicksOnTheSameLink() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = signupAndGetToken(email, TestCpfs.random());

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(get("/auth/verify-email").param("token", token))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(accounts.findByEmail(email).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }
}
