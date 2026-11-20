package dev.obrenoxs.ticketing.auth.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record AdminBootstrapProperties(
        @DefaultValue("") String email,
        @DefaultValue("") String name,
        @DefaultValue("") String password,
        @DefaultValue("") String cpf) {

    public boolean configured() {
        return !email.isBlank() && !name.isBlank() && !password.isBlank() && !cpf.isBlank();
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[email=" + email + ", password=***, cpf=***]";
    }
}
