package com.loganalyzer.core;

import java.time.LocalDateTime;

/**
 * One parsed log event. Multi-line content (stack traces, pretty-printed
 * payloads) is folded into {@code message} with newline separators, so the
 * record always represents a complete entry.
 *
 * @param timestamp when the entry was logged
 * @param level     severity
 * @param thread    logger thread name
 * @param logger    logger name (usually the class)
 * @param message   full message text including continuation lines
 */
public record LogEntry(LocalDateTime timestamp, Level level, String thread, String logger, String message) {

    /** First line of the message, without any continuation content. */
    public String firstMessageLine() {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
