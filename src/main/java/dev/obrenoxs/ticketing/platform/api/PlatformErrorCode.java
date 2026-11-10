package dev.obrenoxs.ticketing.platform.api;

public enum PlatformErrorCode implements ErrorCode {

    RATE_LIMITED(429);

    private final int status;

    PlatformErrorCode(int status) {
        this.status = status;
    }

    @Override
    public int status() {
        return status;
    }
}
