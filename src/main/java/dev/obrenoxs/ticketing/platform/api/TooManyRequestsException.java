package dev.obrenoxs.ticketing.platform.api;

import java.time.Duration;

public class TooManyRequestsException extends BusinessException {

    private final Duration retryAfter;

    public TooManyRequestsException(ErrorCode errorCode, String message, Duration retryAfter) {
        super(errorCode, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
