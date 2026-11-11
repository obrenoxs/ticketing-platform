package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(4);

    @Test
    void shouldMatchCorrectPassword() {
        String hash = hasher.hash("correct horse battery");
        assertThat(hasher.matches("correct horse battery", hash)).isTrue();
    }

    @Test
    void shouldNotMatchWrongPassword() {
        String hash = hasher.hash("correct horse battery");
        assertThat(hasher.matches("wrong password", hash)).isFalse();
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        assertThat(hasher.hash("same-password")).isNotEqualTo(hasher.hash("same-password"));
    }

    @Test
    void shouldEmbedAlgorithmAndCostInHash() {
        String hash = hasher.hash("some-password");
        assertThat(hash).startsWith("$2").contains("$04$");
        assertThat(hash).hasSizeLessThanOrEqualTo(255);
    }

    @Test
    void shouldVerifyHashCreatedWithAnotherCost() {
        String hashCost6 = new PasswordHasher(6).hash("some-password");
        assertThat(hasher.matches("some-password", hashCost6)).isTrue();
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThatThrownBy(() -> hasher.hash("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPasswordLongerThan72Bytes() {
        String longAscii = "a".repeat(73);
        String longMultiByte = "é".repeat(37); // 74 bytes em UTF-8
        assertThatThrownBy(() -> hasher.hash(longAscii)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash(longMultiByte)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptPasswordWithExactly72Bytes() {
        String exact = "a".repeat(72);
        assertThat(hasher.matches(exact, hasher.hash(exact))).isTrue();
    }

    @Test
    void shouldNotMatchTooLongPasswordNorMalformedHash() {
        String hash = hasher.hash("some-password");
        assertThat(hasher.matches("a".repeat(100), hash)).isFalse();
        assertThat(hasher.matches("some-password", "not-a-bcrypt-hash")).isFalse();
        assertThat(hasher.matches("some-password", null)).isFalse();
    }

    @Test
    void shouldSimulateVerificationWithoutFailing() {
        hasher.simulateVerification("any-password");
        hasher.simulateVerification("a".repeat(100));
        hasher.simulateVerification(null);
    }
}
