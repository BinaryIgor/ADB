package com.binaryigor.adb;

import java.util.List;
import java.util.Random;

public class TestUtils {

    private static final Random RANDOM = new Random();
    private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHAR_UPPER = CHAR_LOWER.toUpperCase();
    private static final String NUMBER = "0123456789";
    private static final String DATA_FOR_RANDOM_STRING = CHAR_LOWER + CHAR_UPPER + NUMBER;

    public static String randomString(int size) {
        var builder = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            var nextChar = DATA_FOR_RANDOM_STRING.charAt(RANDOM.nextInt(DATA_FOR_RANDOM_STRING.length()));
            builder.append(nextChar);
        }
        return builder.toString();
    }

    public static String randomString() {
        return randomString(randomNumber(10, 50));
    }

    public static int randomNumber(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("Max must be greater than min!");
        }
        return min + (RANDOM.nextInt(max - min));
    }

    public static byte[] randomBytes(int size) {
        var bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static byte[] randomBytes() {
        return randomBytes(randomNumber(8, 64));
    }

    public static <T> T randomElement(List<T> elements) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Cannot return random element from empty collection!");
        }
        var randomIdx = RANDOM.nextInt(elements.size());
        return elements.get(randomIdx);
    }
}
