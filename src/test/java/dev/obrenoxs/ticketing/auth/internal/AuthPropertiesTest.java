package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {

    @EnableConfigurationProperties(AuthProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void shouldUseCostTenByDefault() {
        runner.run(ctx -> assertThat(ctx.getBean(AuthProperties.class).bcryptCost()).isEqualTo(10));
    }

    @Test
    void shouldFailWhenCostIsTooLowOrTooHigh() {
        runner.withPropertyValues("app.auth.bcrypt-cost=3").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("app.auth.bcrypt-cost=16").run(ctx -> assertThat(ctx).hasFailed());
    }
}
