package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.bootstrap.admin.email=seed-admin@example.com",
        "app.bootstrap.admin.name=Seed Admin",
        "app.bootstrap.admin.password=admin-strong-password",
        "app.bootstrap.admin.cpf=11144477735",
        "app.seed.enabled=true",
        "app.seed.password=seed-strong-password"
})
class DevDataSeederIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    OrganizerProfileRepository profiles;

    @Autowired
    AccountRepository accounts;

    @Autowired
    DevDataSeeder seeder;

    private UUID idOf(String email) {
        return accounts.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void shouldCreateLoginReadyAccountsWithTheRightProfiles() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"buyer@example.com\",\"password\":\"seed-strong-password\"}"))
                .andExpect(status().isOk());

        assertThat(profiles.findById(idOf("organizer@example.com")).orElseThrow().getStatus())
                .isEqualTo(OrganizerStatus.APPROVED);
        assertThat(profiles.findById(idOf("organizer@example.com")).orElseThrow().getDecidedBy())
                .isEqualTo(idOf("seed-admin@example.com"));
        assertThat(profiles.findById(idOf("pending@example.com")).orElseThrow().getStatus())
                .isEqualTo(OrganizerStatus.PENDING);
    }

    @Test
    void shouldBeIdempotent() {
        seeder.run(null);
        seeder.run(null);

        assertThat(accounts.existsByEmail("buyer@example.com")).isTrue();
        assertThat(profiles.findById(idOf("organizer@example.com")).orElseThrow().getStatus())
                .isEqualTo(OrganizerStatus.APPROVED);
    }
}
