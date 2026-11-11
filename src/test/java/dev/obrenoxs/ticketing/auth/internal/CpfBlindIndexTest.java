package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CpfBlindIndexTest {

    private static byte[] key(int fill) {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) fill);
        return k;
    }

    private final CpfBlindIndex index = new CpfBlindIndex(key(1));

    @Test
    void shouldBeDeterministic() {
        Cpf cpf = Cpf.of("52998224725");
        assertThat(index.hash(cpf)).isEqualTo(index.hash(Cpf.of("529.982.247-25")));
    }

    @Test
    void shouldDifferForDifferentCpfs() {
        assertThat(index.hash(Cpf.of("52998224725")))
                .isNotEqualTo(index.hash(Cpf.of("11144477735")));
    }

    @Test
    void shouldDifferForDifferentKeys() {
        Cpf cpf = Cpf.of("52998224725");
        assertThat(index.hash(cpf)).isNotEqualTo(new CpfBlindIndex(key(2)).hash(cpf));
    }

    @Test
    void shouldProduce32Bytes() {
        assertThat(index.hash(Cpf.of("52998224725"))).hasSize(32);
    }

    @Test
    void shouldNotBePlainSha256() throws Exception {
        Cpf cpf = Cpf.of("52998224725");
        byte[] plain = MessageDigest.getInstance("SHA-256")
                .digest(cpf.digits().getBytes(StandardCharsets.UTF_8));
        assertThat(index.hash(cpf)).isNotEqualTo(plain);
    }
}
