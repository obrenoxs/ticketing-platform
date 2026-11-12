package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.AccessDeniedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@AutoConfigureMockMvc
class AdminOrganizerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    OrganizerProfileRepository profiles;

    @Autowired
    AdminOrganizerController controller;

    private static byte[] random(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private UUID newAccount(Role role) {
        UUID id = UUID.randomUUID();
        accounts.saveAndFlush(Account.register(id, id + "@example.com", "Test", "hash",
                random(40), 1, random(32), NOW, role));
        return id;
    }

    private UUID pendingOrganizer() {
        UUID id = newAccount(Role.ORGANIZER);
        profiles.saveAndFlush(OrganizerProfile.request(id, "Rock Producoes", NOW));
        return id;
    }

    private static RequestPostProcessor admin(UUID adminId) {
        return jwt().jwt(j -> j.subject(adminId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void methodSecurityShouldBlockNonAdminCallingTheControllerDirectly() {
        assertThatThrownBy(() -> controller.pending(0, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityShouldAllowAdmin() {
        assertThatCode(() -> controller.pending(0, 20)).doesNotThrowAnyException();
    }

    @Test
    void shouldListPendingOrganizers() throws Exception {
        UUID organizer = pendingOrganizer();

        mvc.perform(get("/admin/organizers/pending").with(admin(newAccount(Role.BUYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId").value(hasItem(organizer.toString())));
    }

    @Test
    void shouldApproveAndRecordWhoDecided() throws Exception {
        UUID organizer = pendingOrganizer();
        UUID adminId = newAccount(Role.BUYER);

        mvc.perform(post("/admin/organizers/{id}/approve", organizer).with(admin(adminId)))
                .andExpect(status().isNoContent());

        OrganizerProfile saved = profiles.findById(organizer).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrganizerStatus.APPROVED);
        assertThat(saved.getDecidedBy()).isEqualTo(adminId);
    }

    @Test
    void shouldReturn409WhenDecidingTwice() throws Exception {
        UUID organizer = pendingOrganizer();
        UUID adminId = newAccount(Role.BUYER);
        mvc.perform(post("/admin/organizers/{id}/approve", organizer).with(admin(adminId)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/admin/organizers/{id}/reject", organizer).with(admin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZER_ALREADY_DECIDED"));
    }

    @Test
    void shouldReturn404ForUnknownOrganizer() throws Exception {
        mvc.perform(post("/admin/organizers/{id}/approve", UUID.randomUUID())
                        .with(admin(newAccount(Role.BUYER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_FOUND"));
    }

    @Test
    void shouldRejectWithReason() throws Exception {
        UUID organizer = pendingOrganizer();

        mvc.perform(post("/admin/organizers/{id}/reject", organizer).with(admin(newAccount(Role.BUYER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Incomplete data  \"}"))
                .andExpect(status().isNoContent());

        OrganizerProfile saved = profiles.findById(organizer).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrganizerStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo("Incomplete data");
    }

    @Test
    void shouldRefuseRejectionWithoutReason() throws Exception {
        UUID organizer = pendingOrganizer();

        mvc.perform(post("/admin/organizers/{id}/reject", organizer).with(admin(newAccount(Role.BUYER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(profiles.findById(organizer).orElseThrow().getStatus())
                .isEqualTo(OrganizerStatus.PENDING);
    }

    @Test
    void shouldKeepBuyersOutOfTheAdminApi() throws Exception {
        UUID organizer = pendingOrganizer();

        mvc.perform(post("/admin/organizers/{id}/approve", organizer)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());

        assertThat(profiles.findById(organizer).orElseThrow().getStatus())
                .isEqualTo(OrganizerStatus.PENDING);
    }
}
