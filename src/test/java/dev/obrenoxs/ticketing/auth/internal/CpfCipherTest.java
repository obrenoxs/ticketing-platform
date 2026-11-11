package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfCipherTest {

    private static byte[] key(int fill) {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) fill);
        return k;
    }

    private final Cpf cpf = Cpf.of("52998224725");
    private final CpfCipher cipher = new CpfCipher(Map.of(1, key(1)), 1);

    @Test
    void shouldDecryptWhatItEncrypted() {
        EncryptedCpf encrypted = cipher.encrypt(cpf);
        assertThat(cipher.decrypt(encrypted.bytes())).isEqualTo(cpf);
    }

    @Test
    void shouldProduceDifferentCiphertextForSameCpf() {
        byte[] first = cipher.encrypt(cpf).bytes();
        byte[] second = cipher.encrypt(cpf).bytes();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldStoreKeyVersionAsFirstByte() {
        EncryptedCpf encrypted = cipher.encrypt(cpf);
        assertThat(encrypted.keyVersion()).isEqualTo(1);
        assertThat(encrypted.bytes()[0]).isEqualTo((byte) 1);
    }

    @Test
    void shouldNotContainPlainDigits() {
        String asText = new String(cipher.encrypt(cpf).bytes(), StandardCharsets.ISO_8859_1);
        assertThat(asText).doesNotContain("52998224725");
    }

    @Test
    void shouldDecryptOldVersionAfterKeyRotation() {
        byte[] oldBlob = cipher.encrypt(cpf).bytes();

        CpfCipher rotated = new CpfCipher(Map.of(1, key(1), 2, key(2)), 2);

        assertThat(rotated.decrypt(oldBlob)).isEqualTo(cpf);
        EncryptedCpf fresh = rotated.encrypt(cpf);
        assertThat(fresh.keyVersion()).isEqualTo(2);
        assertThat(rotated.decrypt(fresh.bytes())).isEqualTo(cpf);
    }

    @Test
    void shouldFailWhenKeyVersionIsUnknown() {
        byte[] oldBlob = cipher.encrypt(cpf).bytes();
        CpfCipher onlyV2 = new CpfCipher(Map.of(2, key(2)), 2);
        assertThatThrownBy(() -> onlyV2.decrypt(oldBlob)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWithWrongKey() {
        byte[] blob = cipher.encrypt(cpf).bytes();
        CpfCipher other = new CpfCipher(Map.of(1, key(9)), 1);
        assertThatThrownBy(() -> other.decrypt(blob)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenCiphertextIsTampered() {
        byte[] blob = cipher.encrypt(cpf).bytes();
        blob[blob.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(blob)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenVersionByteIsTampered() {
        CpfCipher ring = new CpfCipher(Map.of(1, key(1), 2, key(1)), 1);
        byte[] blob = ring.encrypt(cpf).bytes();
        blob[0] = 2; // mesma chave, mas a versão é autenticada (AAD)
        assertThatThrownBy(() -> ring.decrypt(blob)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectMalformedBlob() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectInvalidKeyRing() {
        assertThatThrownBy(() -> new CpfCipher(Map.of(1, new byte[16]), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CpfCipher(Map.of(1, key(1)), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
