package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSecretsStartupTest {

    @EnableConfigurationProperties(AuthSecrets.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void shouldFailToStartWhenSecretsAreMissing() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void shouldStartWhenSecretsAreProvided() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        runner.withPropertyValues(
                        "app.auth.secrets.cpf-encryption-key=" + key,
                        "app.auth.secrets.cpf-hmac-key=" + key,
                        "app.auth.secrets.jwt-signing-key=" + key)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
