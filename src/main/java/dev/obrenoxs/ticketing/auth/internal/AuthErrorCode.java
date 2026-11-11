package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.platform.api.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

    INVALID_VERIFICATION_TOKEN(400),
    INVALID_CREDENTIALS(401);

    private final int status;

    AuthErrorCode(int status) {
        this.status = status;
    }

    @Override
    public int status() {
        return status;
    }
}
