package com.loganalyzer.parser;

import com.loganalyzer.LogAnalyzerException;
import com.loganalyzer.core.Level;
import com.loganalyzer.core.LogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {

    @TempDir
    Path tempDir;

    private final LogParser parser = new LogParser();

    @Test
    void parsesASingleEntryCorrectly() throws IOException {
        Path log = write("""
                2026-11-17 10:23:45.123 ERROR [main] com.foo.Bar - Something went wrong
                """);
        List<LogEntry> entries = parser.parseAll(log);

        assertEquals(1, entries.size());
        LogEntry entry = entries.get(0);
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 23, 45, 123_000_000), entry.timestamp());
        assertEquals(Level.ERROR, entry.level());
        assertEquals("main", entry.thread());
        assertEquals("com.foo.Bar", entry.logger());
        assertEquals("Something went wrong", entry.message());
    }

    @Test
    void foldsStackTraceLinesIntoTheEntry() throws IOException {
        Path log = write("""
                2026-11-17 14:02:11.018 ERROR [exec-1] com.shop.Pay - payment timed out for order 5157
                java.net.SocketTimeoutException: Read timed out
                \tat java.base/java.net.SocketInputStream.read(SocketInputStream.java:186)
                \tat com.shop.Pay.executeCall(Pay.java:214)
                \t... 12 more
                2026-11-17 14:03:00.000 INFO  [exec-1] com.shop.Pay - recovered
                """);
        List<LogEntry> entries = parser.parseAll(log);

        assertEquals(2, entries.size());
        String message = entries.get(0).message();
        assertTrue(message.startsWith("payment timed out for order 5157"));
        assertTrue(message.contains("java.net.SocketTimeoutException: Read timed out"));
        assertTrue(message.contains("at com.shop.Pay.executeCall(Pay.java:214)"));
        assertEquals(Level.INFO, entries.get(1).level());
    }

    @Test
    void foldsCausedByChainsIntoTheEntry() throws IOException {
        Path log = write("""
                2026-11-17 14:08:30.912 ERROR [exec-2] com.shop.Pool - pool exhausted
                java.util.concurrent.TimeoutException: no free connection
                Caused by: com.shop.PoolSaturatedException: saturated
                \tat com.shop.Pool.markSaturated(Pool.java:203)
                2026-11-17 14:09:00.000 INFO  [exec-2] com.shop.Pool - recovered
                """);
        List<LogEntry> entries = parser.parseAll(log);

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).message().contains("Caused by: com.shop.PoolSaturatedException"));
    }

    @Test
    void blankLinesTerminateEntriesAndAreSkipped() throws IOException {
        Path log = write("""
                2026-11-17 09:00:00.000 INFO  [main] com.app.Start - booting

                2026-11-17 09:00:01.000 INFO  [main] com.app.Start - ready
                """);
        List<LogEntry> entries = parser.parseAll(log);

        assertEquals(2, entries.size());
        assertEquals("booting", entries.get(0).message());
        assertEquals("ready", entries.get(1).message());
    }

    @Test
    void unrecognizedLinesBeforeTheFirstEntryAreSkipped() throws IOException {
        Path log = write("""
                Logfile started at boot
                JVM args: -Xmx2g
                2026-11-17 09:00:00.000 INFO  [main] com.app.Start - ready
                """);
        List<LogEntry> entries = parser.parseAll(log);

        assertEquals(1, entries.size());
        assertEquals("ready", entries.get(0).message());
    }

    @Test
    void invalidTimestampFailsFastWithLineNumber() throws IOException {
        Path log = write("""
                2026-11-17 09:00:00.000 INFO  [main] com.app.A - fine
                2026-02-30 09:00:00.000 INFO  [main] com.app.B - february 30th does not exist
                """);
        LogAnalyzerException exception = assertThrows(
                LogAnalyzerException.class, () -> parser.parseAll(log));
        assertTrue(exception.getMessage().contains("line 2"), () -> exception.getMessage());
    }

    private Path write(String content) throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, content);
        return file;
    }
}
