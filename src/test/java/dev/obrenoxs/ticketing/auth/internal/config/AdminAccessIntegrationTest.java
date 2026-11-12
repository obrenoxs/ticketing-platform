package dev.obrenoxs.ticketing.auth.internal.config;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mvc.perform(get("/admin/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403ForBuyer() throws Exception {
        mvc.perform(get("/admin/anything").with(as("BUYER"))).andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403ForOrganizer() throws Exception {
        mvc.perform(get("/admin/anything").with(as("ORGANIZER"))).andExpect(status().isForbidden());
    }

    @Test
    void shouldLetAdminPastTheFence() throws Exception {
        mvc.perform(get("/admin/anything").with(as("ADMIN")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
