package dev.obrenoxs.ticketing.auth.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @Min(4) @Max(15) @DefaultValue("10") int bcryptCost,
        @NotNull @DefaultValue("24h") Duration emailVerificationTtl,
        @NotBlank @DefaultValue("http://localhost:8080") String publicBaseUrl) {
}