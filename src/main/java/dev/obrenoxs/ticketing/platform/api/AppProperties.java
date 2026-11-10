package dev.obrenoxs.ticketing.platform.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull @DefaultValue Reservation reservation,
        @Valid @NotNull @DefaultValue Payment payment,
        @Valid @NotNull @DefaultValue Outbox outbox,
        @Valid @NotNull @DefaultValue Idempotency idempotency,
        @Valid @NotNull @DefaultValue Cache cache,
        @Valid @NotNull @DefaultValue RateLimit rateLimit) {

    public record Reservation(
            @NotNull @DefaultValue("15m") Duration ttl,
            @Min(1) @DefaultValue("10") int maxTicketsPerAccountEvent,
            @NotNull @DefaultValue("30s") Duration minTimeToPay) {}

    public record Payment(
            @Min(1) @DefaultValue("5") int maxAttempts,
            @NotNull @DefaultValue("60s") Duration reconciliationInterval,
            @NotNull @DefaultValue("2m") Duration reconciliationStuckAfter) {}

    public record Outbox(
            @Min(1) @DefaultValue("5") int maxAttempts,
            @NotNull @DefaultValue("30s") Duration backoffBase,
            @NotNull @DefaultValue("5m") Duration stuckAfter) {}

    public record Idempotency(
            @NotNull @DefaultValue("60s") Duration inProgressTimeout,
            @NotNull @DefaultValue("24h") Duration retention) {}

    public record Cache(
            @NotNull @DefaultValue("1s") Duration softTtl,
            @NotNull @DefaultValue("5s") Duration hardTtl) {}

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @Min(1) @DefaultValue("60") int readsPerMinute,
            @Min(1) @DefaultValue("5") int reservesPerMinute,
            @Min(1) @DefaultValue("10") int paymentsPerMinute,
            @Min(1) @DefaultValue("20") int notificationsPerMinute) {}
}
