package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.*;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.EmailVerificationTokenRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SignupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final byte[] KEY = new byte[32];

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OrganizerProfileRepository organizerProfiles = mock(OrganizerProfileRepository.class);
    private final EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
    private final PasswordHasher hasher = spy(new PasswordHasher(4));
    private final List<String> sentLinks = new ArrayList<>();
    private final EmailVerificationNotifier notifier = (email, name, link) -> sentLinks.add(link);

    private final SignupService service = new SignupService(
            accounts, tokens, hasher,
            new CpfCipher(Map.of(1, KEY), 1), new CpfBlindIndex(KEY),
            UUID::randomUUID, () -> NOW, notifier,
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            new AuthProperties(4, Duration.ofHours(24), "http://localhost:8080",
                    Duration.ofMinutes(15), Duration.ofDays(15)),
           organizerProfiles);

    @Test
    void shouldCreateAccountAndTokenForNewUser() {
        service.signup("Ana", "Ana@Example.com", "password-123", "529.982.247-25");

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<EmailVerificationToken> token = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(accounts).saveAndFlush(account.capture());
        verify(tokens).saveAndFlush(token.capture());

        assertThat(account.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(account.getValue().getStatus()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(account.getValue().getPasswordHash()).isNotEqualTo("password-123");
        assertThat(new String(account.getValue().getCpfEncrypted(), StandardCharsets.ISO_8859_1))
                .doesNotContain("52998224725");
        assertThat(token.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void shouldStoreOnlyTheHashOfTheToken() {
        service.signup("Ana", "ana@example.com", "password-123", "52998224725");

        ArgumentCaptor<EmailVerificationToken> token = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).saveAndFlush(token.capture());

        String link = sentLinks.get(0);
        String rawToken = link.substring(link.indexOf("token=") + 6);
        assertThat(link).startsWith("http://localhost:8080/auth/verify-email?token=");
        assertThat(token.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(rawToken));
    }

    @Test
    void shouldDoNothingWhenEmailAlreadyExistsButStillHashPassword() {
        when(accounts.existsByEmail("ana@example.com")).thenReturn(true);

        service.signup("Ana", "ana@example.com", "password-123", "52998224725");

        verify(hasher).hash("password-123");
        verify(accounts, never()).saveAndFlush(any());
        assertThat(sentLinks).isEmpty();
    }

    @Test
    void shouldDoNothingWhenCpfAlreadyExists() {
        when(accounts.existsByCpfHmac(any())).thenReturn(true);

        service.signup("Ana", "new@example.com", "password-123", "52998224725");

        verify(accounts, never()).saveAndFlush(any());
        assertThat(sentLinks).isEmpty();
    }

    @Test
    void shouldStaySilentWhenDatabaseRejectsConcurrentDuplicate() {
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.signup("Ana", "ana@example.com", "password-123", "52998224725");

        assertThat(sentLinks).isEmpty();
    }

    @Test
    void shouldRefuseOrganizerSignupWithoutPublicNameBeforeDoingAnyWork() {
        SignupService service = new SignupService(mock(AccountRepository.class),
                mock(EmailVerificationTokenRepository.class), mock(PasswordHasher.class),
                mock(CpfCipher.class), mock(CpfBlindIndex.class), mock(IdGenerator.class),
                mock(DatabaseClock.class), mock(EmailVerificationNotifier.class),
                mock(TransactionTemplate.class), mock(AuthProperties.class),
                mock(OrganizerProfileRepository.class));

        assertThatThrownBy(() -> service.signup("Ana", "ana@example.com", "password-123",
                "11144477735", AccountType.ORGANIZER, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSaveAPendingProfileWhenSigningUpAnOrganizer() {
        service.signup("Ana", "ana@example.com", "password-123", "52998224725",
                AccountType.ORGANIZER, "  Rock Producoes  ");

        ArgumentCaptor<OrganizerProfile> profile = ArgumentCaptor.forClass(OrganizerProfile.class);
        verify(organizerProfiles).saveAndFlush(profile.capture());

        assertThat(profile.getValue().getStatus()).isEqualTo(OrganizerStatus.PENDING);
        assertThat(profile.getValue().getPublicName()).isEqualTo("Rock Producoes");
    }

    @Test
    void shouldNotCreateAProfileForABuyer() {
        service.signup("Ana", "ana@example.com", "password-123", "52998224725");

        verify(organizerProfiles, never()).saveAndFlush(any());
    }
}
