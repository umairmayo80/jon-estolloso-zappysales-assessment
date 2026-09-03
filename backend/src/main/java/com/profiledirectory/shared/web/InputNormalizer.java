package com.profiledirectory.shared.web;

import java.util.Locale;

public final class InputNormalizer {
    private InputNormalizer() {
    }

    public static String required(String value) {
        return value == null ? null : value.trim();
    }

    public static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static String email(String value) {
        String normalized = required(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public static String countryCode(String value) {
        String normalized = required(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
