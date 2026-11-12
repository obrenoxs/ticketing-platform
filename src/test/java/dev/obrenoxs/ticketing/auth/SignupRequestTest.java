package dev.obrenoxs.ticketing.auth;



import dev.obrenoxs.ticketing.auth.internal.AccountType;
import dev.obrenoxs.ticketing.auth.internal.web.SignupRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignupRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<ConstraintViolation<SignupRequest>> validate(AccountType type, String organizerName) {
        return VALIDATOR.validate(new SignupRequest(
                "Ana", "ana@example.com", "password-123", "11144477735", type, organizerName));
    }

    @Test
    void shouldAcceptBuyerWhenTypeIsOmittedOrExplicit() {
        assertThat(validate(null, null)).isEmpty();
        assertThat(validate(AccountType.BUYER, null)).isEmpty();
    }

    @Test
    void shouldAcceptOrganizerWithPublicName() {
        assertThat(validate(AccountType.ORGANIZER, "Rock Producoes")).isEmpty();
    }

    @Test
    void shouldRejectOrganizerWithoutPublicName() {
        assertThat(validate(AccountType.ORGANIZER, null)).isNotEmpty();
        assertThat(validate(AccountType.ORGANIZER, "   ")).isNotEmpty();
    }

    @Test
    void shouldRejectBuyerThatSendsPublicName() {
        assertThat(validate(AccountType.BUYER, "Rock Producoes")).isNotEmpty();
        assertThat(validate(null, "Rock Producoes")).isNotEmpty();
    }
}
