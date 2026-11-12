package dev.obrenoxs.ticketing.auth.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account extends AssignedIdEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "cpf_encrypted", nullable = false)
    private byte[] cpfEncrypted;

    @Column(name = "cpf_key_version", nullable = false)
    private short cpfKeyVersion;

    @Column(name = "cpf_hmac", nullable = false)
    private byte[] cpfHmac;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    private Account(UUID id) {
        super(id);
    }

    public static Account register(UUID id, String email, String name, String passwordHash,
                                   byte[] cpfEncrypted, int cpfKeyVersion, byte[] cpfHmac,
                                   Instant now) {
        return register(id, email, name, passwordHash, cpfEncrypted, cpfKeyVersion, cpfHmac,
                now, Role.BUYER);
    }

    public static Account register(UUID id, String email, String name, String passwordHash,
                                   byte[] cpfEncrypted, int cpfKeyVersion, byte[] cpfHmac,
                                   Instant now, Role role) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN accounts are not created through registration.");
        }
        Account account = new Account(id);
        account.email = normalizeEmail(email);
        account.name = name.trim();
        account.passwordHash = passwordHash;
        account.role = role;
        account.status = AccountStatus.PENDING_EMAIL;
        account.cpfEncrypted = cpfEncrypted;
        account.cpfKeyVersion = (short) cpfKeyVersion;
        account.cpfHmac = cpfHmac;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public void activate(Instant now) {
        if (status != AccountStatus.PENDING_EMAIL) {
            throw new IllegalStateException("Account is not waiting for email verification.");
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public byte[] getCpfEncrypted() { return cpfEncrypted; }
    public int getCpfKeyVersion() { return cpfKeyVersion; }
    public byte[] getCpfHmac() { return cpfHmac; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}