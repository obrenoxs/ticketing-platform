package dev.obrenoxs.ticketing.auth.internal;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.secrets")
public record AuthSecrets(String cpfEncryptionKey, String cpfHmacKey, String jwtSigningKey) {

    private static final int KEY_BYTES = 32;

    public AuthSecrets {
        requireKey("CPF_ENCRYPTION_KEY", cpfEncryptionKey, true);
        requireKey("CPF_HMAC_KEY", cpfHmacKey, false);
        requireKey("JWT_SIGNING_KEY", jwtSigningKey, false);
    }

    byte[] cpfEncryptionKeyBytes() {
        return Base64.getDecoder().decode(cpfEncryptionKey);
    }

    byte[] cpfHmacKeyBytes() {
        return Base64.getDecoder().decode(cpfHmacKey);
    }

    public byte[] jwtSigningKeyBytes() {
        return Base64.getDecoder().decode(jwtSigningKey);
    }

    private static void requireKey(String envName, String value, boolean exactLength) {
        String hint = " Run scripts/generate-secrets.sh to create it.";
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(envName + " is missing." + hint);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(envName + " is not valid base64." + hint);
        }
        boolean valid = exactLength ? decoded.length == KEY_BYTES : decoded.length >= KEY_BYTES;
        if (!valid) {
            throw new IllegalArgumentException(envName + " must have "
                    + (exactLength ? "exactly " : "at least ") + KEY_BYTES + " bytes." + hint);
        }
    }

    @Override
    public String toString() {
        return "AuthSecrets[redacted]";
    }
}