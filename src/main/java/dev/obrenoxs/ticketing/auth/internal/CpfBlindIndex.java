package dev.obrenoxs.ticketing.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class CpfBlindIndex {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    @Autowired
    CpfBlindIndex(AuthSecrets secrets) {
        this(secrets.cpfHmacKeyBytes());
    }

    CpfBlindIndex(byte[] key) {
        this.key = new SecretKeySpec(key, ALGORITHM);
    }

    byte[] hash(Cpf cpf) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(cpf.digits().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute CPF index.", e);
        }
    }
}