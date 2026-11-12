package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.AccountType;
import dev.obrenoxs.ticketing.auth.internal.ValidCpf;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record SignupRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @ValidCpf String cpf,
        AccountType accountType,
        @Size(max = 150) String organizerName) {

    @AssertTrue(message = "must have at most 72 bytes")
    boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @AssertTrue(message = "is required for ORGANIZER accounts and not allowed for BUYER accounts")
    boolean isOrganizerNameConsistent() {
        boolean organizer = resolvedAccountType() == AccountType.ORGANIZER;
        boolean hasName = organizerName != null && !organizerName.isBlank();
        return organizer == hasName;
    }

    AccountType resolvedAccountType() {
        return accountType == null ? AccountType.BUYER : accountType;
    }
}
