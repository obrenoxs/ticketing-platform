package dev.obrenoxs.ticketing.auth.internal;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class PasswordHasher {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    @Autowired
    PasswordHasher(AuthProperties properties) {
        this(properties.bcryptCost());
    }

    PasswordHasher(int cost) {
        this.encoder = new BCryptPasswordEncoder(cost);
        this.dummyHash = encoder.encode("timing-equalizer");
    }

    String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank.");
        }
        if (tooLong(rawPassword)) {
            throw new IllegalArgumentException("Password must have at most 72 bytes.");
        }
        return encoder.encode(rawPassword);
    }

    boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || tooLong(rawPassword) || hash == null) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }

    void simulateVerification(String rawPassword) {
        if (rawPassword != null && !tooLong(rawPassword)) {
            encoder.matches(rawPassword, dummyHash);
        }
    }

    private static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}