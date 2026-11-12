package dev.obrenoxs.ticketing.auth.internal;

import com.jayway.jsonpath.JsonPath;
import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordHasher hasher;

    @Autowired
    JdbcTemplate jdbc;

    private record Session(UUID accountId, String accessToken, String refreshToken) {}

    private UUID activeAccount(String email) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Account account = Account.register(UUID.randomUUID(), email, "Ana", hasher.hash("password-123"),
                new byte[] {1}, 1, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8), now);
        account.activate(now);
        return accounts.saveAndFlush(account).getId();
    }

    private Session loginTo(String email, UUID accountId) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password-123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Session(accountId, JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    private Session loggedIn() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        return loginTo(email, activeAccount(email));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private int liveTokens(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from refresh_token where account_id = ? and revoked_at is null",
                Integer.class, accountId);
    }

    private int allTokens(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from refresh_token where account_id = ?", Integer.class, accountId);
    }

    @Test
    void shouldRotateAndIssueAWorkingAccessToken() throws Exception {
        Session session = loggedIn();

        String body = refresh(session.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(body, "$.refreshToken");
        String newAccess = JsonPath.read(body, "$.accessToken");

        assertThat(newRefresh).isNotEqualTo(session.refreshToken());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(session.accountId().toString()));
    }

    @Test
    void shouldRefreshEvenWhenTheClientStillSendsAnExpiredAccessToken() throws Exception {
        Session session = loggedIn();

        mvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(session.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRevokeTheWholeFamilyAndPersistItWhenAUsedTokenIsReplayed() throws Exception {
        Session session = loggedIn();
        String body = refresh(session.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(body, "$.refreshToken");

        refresh(session.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());

        assertThat(allTokens(session.accountId())).isEqualTo(2);
        assertThat(liveTokens(session.accountId())).isZero();
    }

    @Test
    void shouldRevokeSessionOnLogoutAndStayQuietForUnknownTokens() throws Exception {
        Session session = loggedIn();

        logout(session.refreshToken()).andExpect(status().isNoContent());
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        logout(session.refreshToken()).andExpect(status().isNoContent());
        logout("never-existed").andExpect(status().isNoContent());

        assertThat(liveTokens(session.accountId())).isZero();
    }

    @Test
    void shouldKeepOtherSessionsOfTheSameAccountAfterLogout() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UUID accountId = activeAccount(email);
        Session phone = loginTo(email, accountId);
        Session laptop = loginTo(email, accountId);

        logout(phone.refreshToken()).andExpect(status().isNoContent());

        refresh(phone.refreshToken()).andExpect(status().isUnauthorized());
        refresh(laptop.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void shouldRefuseExpiredTokenWithoutTreatingItAsAnAttack() throws Exception {
        Session session = loggedIn();
        jdbc.update("update refresh_token set expires_at = now() - interval '1 minute' where account_id = ?",
                session.accountId());

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        assertThat(liveTokens(session.accountId())).isEqualTo(1);
    }
}