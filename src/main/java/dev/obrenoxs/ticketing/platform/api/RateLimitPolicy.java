package dev.obrenoxs.ticketing.platform.api;

public enum RateLimitPolicy {
    READS(false), RESERVE(false), PAYMENT(false), NOTIFICATIONS(false),
    LOGIN(true), LOGIN_FAILURE(false), SIGNUP(true);

    private final boolean byIp;

    RateLimitPolicy(boolean byIp) {
        this.byIp = byIp;
    }

    public boolean byIp() {
        return byIp;
    }
}
