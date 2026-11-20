package dev.obrenoxs.ticketing.auth.api;

public interface CurrentAccountProvider {
    CurrentAccount require();
}
