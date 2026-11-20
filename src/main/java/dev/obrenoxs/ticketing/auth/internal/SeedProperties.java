package dev.obrenoxs.ticketing.auth.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String password) {

    @Override
    public String toString() {
        return "SeedProperties[enabled=" + enabled + ", password=***]";
    }
}
