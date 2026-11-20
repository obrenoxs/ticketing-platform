package dev.obrenoxs.ticketing.auth.internal;

import com.jayway.jsonpath.JsonPath;
import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminBootstrapIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AdminBootstrapService bootstrap;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void shouldCreateAnAdminWhoCanLoginAndOpenTheAdminArea() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var props = new AdminBootstrapProperties(email, "Admin Teste", "a-strong-password", TestCpfs.random());

        assertThat(bootstrap.bootstrap(props)).isEqualTo(AdminBootstrapService.Outcome.CREATED);

        String token = login(email, "a-strong-password");
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/admin/organizers/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotChangeThePasswordWhenRunAgain() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String cpf = TestCpfs.random();
        bootstrap.bootstrap(new AdminBootstrapProperties(email, "Admin", "a-strong-password", cpf));

        var again = bootstrap.bootstrap(new AdminBootstrapProperties(email, "Admin", "another-strong-password", cpf));

        assertThat(again).isEqualTo(AdminBootstrapService.Outcome.ALREADY_EXISTS);
        login(email, "a-strong-password"); // a senha original continua valendo
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"another-strong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
    }
}
