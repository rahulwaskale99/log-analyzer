package com.loganalyzer;

/**
 * Unchecked exception carrying a user-facing message about what went wrong
 * while parsing or analyzing logs. Parser failures include the line number
 * where the problem was detected.
 */
public class LogAnalyzerException extends RuntimeException {

    public LogAnalyzerException(String message) {
        super(message);
    }

    public LogAnalyzerException(String message, Throwable cause) {
        super(message, cause);
    }
}
