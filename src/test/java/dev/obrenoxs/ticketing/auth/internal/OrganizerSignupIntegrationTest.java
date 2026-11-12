package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrganizerSignupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions signup(String email, String accountTypeJson, String organizerNameJson) throws Exception {
        String body = """
                {"name":"Ana","email":"%s","password":"password-123","cpf":"%s"%s%s}
                """.formatted(email, TestCpfs.random(), accountTypeJson, organizerNameJson);
        return mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int accounts(String email) {
        return jdbc.queryForObject("select count(*) from account where email = ?", Integer.class, email);
    }

    @Test
    void shouldCreateOrganizerAccountTogetherWithPendingProfile() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        signup(email, ",\"accountType\":\"ORGANIZER\"", ",\"organizerName\":\"  Rock Producoes  \"")
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select role from account where email = ?", String.class, email))
                .isEqualTo("ORGANIZER");
        Map<String, Object> profile = jdbc.queryForMap(
                "select p.status, p.public_name from organizer_profile p "
                        + "join account a on a.id = p.account_id where a.email = ?", email);
        assertThat(profile.get("status")).isEqualTo("PENDING");
        assertThat(profile.get("public_name")).isEqualTo("Rock Producoes");
    }

    @Test
    void shouldCreateBuyerWithoutAnyOrganizerProfile() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        signup(email, "", "").andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select role from account where email = ?", String.class, email))
                .isEqualTo("BUYER");
        assertThat(jdbc.queryForObject(
                "select count(*) from organizer_profile p join account a on a.id = p.account_id where a.email = ?",
                Integer.class, email)).isZero();
    }

    @Test
    void shouldRefuseAdminSignupAndCreateNothing() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        signup(email, ",\"accountType\":\"ADMIN\"", "").andExpect(status().isBadRequest());

        assertThat(accounts(email)).isZero();
    }

    @Test
    void shouldRefuseOrganizerWithoutPublicNameAndCreateNothing() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        signup(email, ",\"accountType\":\"ORGANIZER\"", "").andExpect(status().isBadRequest());

        assertThat(accounts(email)).isZero();
    }
}
