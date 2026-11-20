package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBootstrapPropertiesTest {

    @Test
    void shouldNotBeConfiguredByDefault() {
        assertThat(new AdminBootstrapProperties("", "", "", "").configured()).isFalse();
    }

    @Test
    void shouldNotBeConfiguredWhenAnyValueIsMissing() {
        assertThat(new AdminBootstrapProperties("a@example.com", "Admin", "", "11144477735").configured())
                .isFalse();
    }

    @Test
    void shouldBeConfiguredWhenEverythingIsProvided() {
        assertThat(new AdminBootstrapProperties("a@example.com", "Admin", "a-long-password", "11144477735")
                .configured()).isTrue();
    }

    @Test
    void shouldNeverPrintSecrets() {
        String printed = new AdminBootstrapProperties("a@example.com", "Admin", "a-long-password", "11144477735")
                .toString();

        assertThat(printed).doesNotContain("a-long-password").doesNotContain("11144477735");
    }
}
