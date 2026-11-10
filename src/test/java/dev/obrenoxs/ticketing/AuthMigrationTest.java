package dev.obrenoxs.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthMigrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    private void insertAccount(String email, String role, String status, byte[] cpfHmac) {
        jdbc.sql("""
                INSERT INTO account (id, email, name, password_hash, role, status,
                                     cpf_encrypted, cpf_key_version, cpf_hmac)
                VALUES (:id, :email, 'Test', 'hash', :role, :status, :enc, 1, :hmac)
                """)
                .param("id", UUID.randomUUID())
                .param("email", email)
                .param("role", role)
                .param("status", status)
                .param("enc", new byte[] {1})
                .param("hmac", cpfHmac)
                .update();
    }

    @Test
    void shouldAcceptValidAccount() {
        insertAccount("ok@example.com", "BUYER", "ACTIVE", new byte[] {1});
    }

    @Test
    void shouldRejectUnknownRole() {
        assertThatThrownBy(() -> insertAccount("a@example.com", "HACKER", "ACTIVE", new byte[] {2}))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectUppercaseEmail() {
        assertThatThrownBy(() -> insertAccount("A@Example.com", "BUYER", "ACTIVE", new byte[] {3}))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateEmail() {
        insertAccount("dup@example.com", "BUYER", "ACTIVE", new byte[] {4});
        assertThatThrownBy(() -> insertAccount("dup@example.com", "BUYER", "ACTIVE", new byte[] {5}))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateCpfHmac() {
        insertAccount("c1@example.com", "BUYER", "ACTIVE", new byte[] {6});
        assertThatThrownBy(() -> insertAccount("c2@example.com", "BUYER", "ACTIVE", new byte[] {6}))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
