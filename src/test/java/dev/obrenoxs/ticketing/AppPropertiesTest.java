package dev.obrenoxs.ticketing;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(AppProperties.class)
    static class Config {}

    @Test
    void shouldLoadBusinessDefaults() {
        runner.run(ctx -> {
            AppProperties p = ctx.getBean(AppProperties.class);
            assertThat(p.reservation().ttl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(p.reservation().maxTicketsPerAccountEvent()).isEqualTo(10);
            assertThat(p.outbox().maxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void shouldFailWhenValueIsInvalid() {
        runner.withPropertyValues("app.reservation.max-tickets-per-account-event=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
