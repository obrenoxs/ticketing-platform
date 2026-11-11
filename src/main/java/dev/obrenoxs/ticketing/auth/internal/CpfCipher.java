package dev.obrenoxs.ticketing.auth.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class CpfCipher {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int HEADER_BYTES = 1 + IV_BYTES;

    private final Map<Integer, SecretKey> keys = new HashMap<>();
    private final int currentVersion;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    CpfCipher(AuthSecrets secrets) {
        this(Map.of(1, secrets.cpfEncryptionKeyBytes()), 1);
    }

    CpfCipher(Map<Integer, byte[]> keyRing, int currentVersion) {
        keyRing.forEach((version, key) -> {
            if (version < 1 || version > 127) {
                throw new IllegalArgumentException("Key version must be between 1 and 127.");
            }
            if (key.length != KEY_BYTES) {
                throw new IllegalArgumentException("Encryption key must have " + KEY_BYTES + " bytes.");
            }
            keys.put(version, new SecretKeySpec(key, "AES"));
        });
        if (!keys.containsKey(currentVersion)) {
            throw new IllegalArgumentException("Current key version is not in the key ring.");
        }
        this.currentVersion = currentVersion;
    }

    EncryptedCpf encrypt(Cpf cpf) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[] {(byte) currentVersion});
            byte[] encrypted = cipher.doFinal(cpf.digits().getBytes(StandardCharsets.UTF_8));

            byte[] blob = ByteBuffer.allocate(HEADER_BYTES + encrypted.length)
                    .put((byte) currentVersion)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return new EncryptedCpf(blob, currentVersion);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt CPF.", e);
        }
    }

    Cpf decrypt(byte[] blob) {
        if (blob == null || blob.length <= HEADER_BYTES) {
            throw new IllegalStateException("Malformed encrypted CPF.");
        }
        int version = blob[0] & 0xFF;
        SecretKey key = keys.get(version);
        if (key == null) {
            throw new IllegalStateException("Unknown CPF key version: " + version);
        }
        try {
            byte[] iv = Arrays.copyOfRange(blob, 1, HEADER_BYTES);
            byte[] encrypted = Arrays.copyOfRange(blob, HEADER_BYTES, blob.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[] {(byte) version});
            byte[] plain = cipher.doFinal(encrypted);
            return new Cpf(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt CPF.", e);
        }
    }
}