package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthSecretsTest {

    private static final String VALID = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void shouldAcceptValidKeys() {
        AuthSecrets secrets = new AuthSecrets(VALID, VALID, VALID);
        assertThat(secrets.cpfEncryptionKeyBytes()).hasSize(32);
    }

    @Test
    void shouldRejectMissingKeyNamingTheVariable() {
        assertThatThrownBy(() -> new AuthSecrets(VALID, "", VALID))
                .hasMessageContaining("CPF_HMAC_KEY");
    }

    @Test
    void shouldRejectInvalidBase64() {
        assertThatThrownBy(() -> new AuthSecrets("not base64!!", VALID, VALID))
                .hasMessageContaining("CPF_ENCRYPTION_KEY");
    }

    @Test
    void shouldRejectShortKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AuthSecrets(VALID, VALID, shortKey))
                .hasMessageContaining("JWT_SIGNING_KEY");
    }

    @Test
    void shouldRequireExactLengthForEncryptionKey() {
        String longKey = Base64.getEncoder().encodeToString(new byte[48]);
        assertThatThrownBy(() -> new AuthSecrets(longKey, VALID, VALID))
                .hasMessageContaining("exactly");
    }

    @Test
    void shouldNotExposeKeysInToString() {
        String key = Base64.getEncoder().encodeToString(new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});
        assertThat(new AuthSecrets(key, key, key).toString()).doesNotContain(key);
    }
}
