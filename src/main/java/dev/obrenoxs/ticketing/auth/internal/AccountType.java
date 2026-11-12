package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Role;

public enum AccountType {
    BUYER(Role.BUYER),
    ORGANIZER(Role.ORGANIZER);

    private final Role role;

    AccountType(Role role) {
        this.role = role;
    }

    public Role role() {
        return role;
    }
}