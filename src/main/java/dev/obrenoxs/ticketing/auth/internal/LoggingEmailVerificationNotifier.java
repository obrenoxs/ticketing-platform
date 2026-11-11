package dev.obrenoxs.ticketing.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingEmailVerificationNotifier implements EmailVerificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailVerificationNotifier.class);

    @Override
    public void send(String email, String name, String verificationLink) {
        log.info("[DEV] Email verification link: {}", verificationLink);
    }
}
