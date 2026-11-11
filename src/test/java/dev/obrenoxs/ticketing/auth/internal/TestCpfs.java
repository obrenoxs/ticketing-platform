package dev.obrenoxs.ticketing.auth.internal;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Collectors;

final class TestCpfs {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestCpfs() {
    }

    static String random() {
        while (true) {
            int[] d = new int[11];
            for (int i = 0; i < 9; i++) {
                d[i] = RANDOM.nextInt(10);
            }
            d[9] = checkDigit(d, 9);
            d[10] = checkDigit(d, 10);
            String digits = Arrays.stream(d).mapToObj(String::valueOf).collect(Collectors.joining());
            if (Cpf.isValid(digits)) {
                return digits;
            }
        }
    }

    private static int checkDigit(int[] d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += d[i] * (length + 1 - i);
        }
        int result = 11 - (sum % 11);
        return result >= 10 ? 0 : result;
    }
}
