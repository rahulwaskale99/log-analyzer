package com.loganalyzer.core;

import java.util.Arrays;

/**
 * Log severity levels, ordered from most to least severe.
 */
public enum Level {
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE;

    /**
     * Case-insensitive, whitespace-tolerant lookup.
     *
     * @throws IllegalArgumentException if the value is not a known level
     */
    public static Level fromString(String value) {
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(level -> level.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown log level: '" + value + "'"));
    }
}
