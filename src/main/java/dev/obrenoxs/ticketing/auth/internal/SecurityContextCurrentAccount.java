package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.api.AccountRole;
import dev.obrenoxs.ticketing.auth.api.CurrentAccount;
import dev.obrenoxs.ticketing.auth.api.CurrentAccountProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class SecurityContextCurrentAccount implements CurrentAccountProvider {

    @Override
    public CurrentAccount require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException("No authenticated account in the current request.");
        }
        Jwt jwt = jwtAuthentication.getToken();
        return new CurrentAccount(
                UUID.fromString(jwt.getSubject()),
                AccountRole.valueOf(jwt.getClaimAsString(AccessTokenService.ROLE_CLAIM)));
    }
}
