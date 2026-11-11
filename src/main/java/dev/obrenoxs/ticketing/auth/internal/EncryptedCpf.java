package dev.obrenoxs.ticketing.auth.internal;

record EncryptedCpf(byte[] bytes, int keyVersion) {}