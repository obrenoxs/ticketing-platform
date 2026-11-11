package dev.obrenoxs.ticketing.auth.internal;

import java.util.regex.Pattern;

public record Cpf(String digits) {

    private static final Pattern FORMAT =
            Pattern.compile("^(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11})$");

    public Cpf {
        if (digits == null || !digits.matches("\\d{11}") || !hasValidCheckDigits(digits)) {
            throw new IllegalArgumentException("Invalid CPF.");
        }
    }

    public static Cpf of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Invalid CPF.");
        }
        String trimmed = raw.trim();
        if (!FORMAT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid CPF.");
        }
        return new Cpf(trimmed.replaceAll("\\D", ""));
    }

    public static boolean isValid(String raw) {
        try {
            of(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean hasValidCheckDigits(String d) {
        if (d.chars().distinct().count() == 1) {
            return false;
        }
        return digitAt(d, 9) == checkDigit(d, 9) && digitAt(d, 10) == checkDigit(d, 10);
    }

    private static int checkDigit(String d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(d, i) * (length + 1 - i);
        }
        int result = 11 - (sum % 11);
        return result >= 10 ? 0 : result;
    }

    private static int digitAt(String d, int index) {
        return d.charAt(index) - '0';
    }

    @Override
    public String toString() {
        return "***.***.***-" + digits.substring(9);
    }
}