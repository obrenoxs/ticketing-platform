package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.util.UUID;

@AutoConfigureMockMvc
class SignupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @MockitoBean
    EmailVerificationNotifier notifier;

    private String json(String email, String cpf) {
        return """
                {"name":"Ana","email":"%s","password":"password-123","cpf":"%s"}
                """.formatted(email, cpf);
    }

    @Test
    void shouldReturnIdenticalResponseForNewAndExistingUsers() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String cpf = "52998224725";

        String first = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, cpf)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        String duplicateEmail = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "11144477735")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        String duplicateCpf = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(UUID.randomUUID() + "@example.com", cpf)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(duplicateEmail).isEqualTo(first);
        assertThat(duplicateCpf).isEqualTo(first);

        Account saved = accounts.findByEmail(email).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.PENDING_EMAIL);
        verify(notifier, times(1)).send(eq(email), any(), startsWith("http://localhost:8080/auth/verify-email?token="));
    }
}
