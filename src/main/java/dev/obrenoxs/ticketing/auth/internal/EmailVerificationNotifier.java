package dev.obrenoxs.ticketing.auth.internal;

interface EmailVerificationNotifier {
    void send(String email, String name, String verificationLink);
}
