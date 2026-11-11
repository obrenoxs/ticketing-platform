package dev.obrenoxs.ticketing.auth.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @Test
    void shouldAcceptFormattedCpf() {
        assertThat(Cpf.of("529.982.247-25").digits()).isEqualTo("52998224725");
    }

    @Test
    void shouldAcceptDigitsOnlyCpf() {
        assertThat(Cpf.of("52998224725").digits()).isEqualTo("52998224725");
    }

    @Test
    void shouldIgnoreSurroundingWhitespace() {
        assertThat(Cpf.of("  529.982.247-25 ").digits()).isEqualTo("52998224725");
    }

    @Test
    void shouldProduceSameCanonicalFormForDifferentInputs() {
        assertThat(Cpf.of("529.982.247-25")).isEqualTo(Cpf.of("52998224725"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11144477735", "12345678909", "00000000191"})
    void shouldAcceptOtherValidCpfs(String value) {
        assertThat(Cpf.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "12345678900"})
    void shouldRejectWrongCheckDigits(String value) {
        assertThat(Cpf.isValid(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    void shouldRejectRepeatedDigits(String value) {
        assertThat(Cpf.isValid(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123", "529982247255", "abc52998224725",
            "529.982.24725", "529-982-247.25", "529.982.247-2x"})
    void shouldRejectMalformedInput(String value) {
        assertThat(Cpf.isValid(value)).isFalse();
    }

    @Test
    void shouldRejectNull() {
        assertThat(Cpf.isValid(null)).isFalse();
    }

    @Test
    void shouldRejectInvalidValueInConstructor() {
        assertThatThrownBy(() -> new Cpf("12345678900"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMaskCpfInToString() {
        assertThat(Cpf.of("52998224725").toString())
                .isEqualTo("***.***.***-25")
                .doesNotContain("529982247");
    }

    @Test
    void shouldNotLeakValueInErrorMessage() {
        assertThatThrownBy(() -> Cpf.of("12345678900"))
                .hasMessageNotContaining("12345678900");
    }
}
