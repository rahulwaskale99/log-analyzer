package com.loganalyzer.parser;

import com.loganalyzer.LogAnalyzerException;
import com.loganalyzer.core.Level;
import com.loganalyzer.core.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Parses logback-style log files of the form
 * {@code 2026-11-17 10:23:45.123 ERROR [main] com.foo.Bar - Something failed}.
 *
 * <p><strong>Multi-line entries.</strong> Lines that do not match the entry
 * pattern (stack trace frames, {@code Caused by: ...}, pretty-printed payload
 * lines) are folded into the message of the entry they follow, joined with
 * {@code \n}. A blank line terminates the current entry.
 *
 * <p><strong>Streaming.</strong> {@link #parse(Path)} returns a lazy
 * {@link Stream} backed by the file reader: entries are produced as they are
 * read and the file is never fully loaded into memory, so multi-GB logs can be
 * analyzed on a laptop. The reader is closed when the stream is closed — use
 * try-with-resources. {@link #parseAll(Path)} is the convenience variant that
 * materializes everything.
 *
 * <p><strong>Leniency.</strong> Blank lines are skipped; unrecognized lines
 * before the first real entry are skipped. A line that matches the timestamp
 * shape but holds an invalid date (e.g. 2026-02-30) fails fast with the line
 * number, because silently dropping entries would corrupt the statistics.
 */
public final class LogParser {

    private static final Pattern ENTRY_START = Pattern.compile(
            "^\\s*(\\d{4}-\\d{2}-\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})"
                    + "\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)"
                    + "\\s+\\[([^\\]]+)]\\s+([\\w.$]+)\\s+-\\s?(.*)\\s*$");

    /** Lazily parsed entries; the underlying reader is closed on stream close. */
    public Stream<LogEntry> parse(Path file) {
        BufferedReader reader;
        try {
            reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LogAnalyzerException("Cannot open log file: " + file, e);
        }
        EntryIterator iterator = new EntryIterator(reader, file.toString());
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                        false)
                .onClose(() -> {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                        // closing a best-effort resource; nothing useful to do
                    }
                });
    }

    /** Materializes every entry; fine for normal log files, use {@link #parse(Path)} for huge ones. */
    public List<LogEntry> parseAll(Path file) {
        try (Stream<LogEntry> entries = parse(file)) {
            return entries.toList();
        }
    }

    /**
     * Iterator over complete entries.
     *
     * <p>State machine with two slots: {@code building} is the entry currently
     * accumulating continuation lines, {@code buffered} is a finished entry
     * waiting to be returned. An entry is finished (and moves to
     * {@code buffered}) the moment the next entry-start line or a blank line
     * appears, or at end of file.
     */
    private static final class EntryIterator implements Iterator<LogEntry> {

        private final BufferedReader reader;
        private final String fileName;
        private long lineNumber;
        private PendingEntry building;
        private LogEntry buffered;
        private boolean drained;

        EntryIterator(BufferedReader reader, String fileName) {
            this.reader = reader;
            this.fileName = fileName;
        }

        @Override
        public boolean hasNext() {
            while (buffered == null && !drained) {
                fill();
            }
            return buffered != null;
        }

        @Override
        public LogEntry next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            LogEntry entry = buffered;
            buffered = null;
            return entry;
        }

        private void fill() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.isBlank()) {
                        if (building != null) {
                            buffered = building.toEntry();
                            building = null;
                            return;
                        }
                        continue;
                    }
                    Matcher matcher = ENTRY_START.matcher(line);
                    if (matcher.matches()) {
                        if (building != null) {
                            buffered = building.toEntry();
                        }
                        building = newEntry(matcher);
                        if (buffered != null) {
                            return; // previous entry complete; current one keeps accumulating
                        }
                        continue; // first entry of the file: nothing to return yet
                    }
                    if (building != null) {
                        building.message.append('\n').append(line);
                    }
                    // else: unrecognized line before the first entry — skip leniently
                }
                drained = true;
                if (building != null) {
                    buffered = building.toEntry();
                    building = null;
                }
            } catch (IOException e) {
                throw new LogAnalyzerException(
                        "I/O error reading " + fileName + " near line " + lineNumber, e);
            }
        }

        private PendingEntry newEntry(Matcher matcher) {
            try {
                LocalDateTime timestamp = LocalDateTime.of(
                        LocalDate.parse(matcher.group(1)),
                        LocalTime.of(
                                Integer.parseInt(matcher.group(2)),
                                Integer.parseInt(matcher.group(3)),
                                Integer.parseInt(matcher.group(4)),
                                Integer.parseInt(matcher.group(5)) * 1_000_000));
                return new PendingEntry(
                        timestamp,
                        Level.fromString(matcher.group(6)),
                        matcher.group(7),
                        matcher.group(8),
                        matcher.group(9));
            } catch (DateTimeParseException | IllegalArgumentException e) {
                throw new LogAnalyzerException(
                        fileName + " line " + lineNumber + ": malformed log entry - " + e.getMessage(), e);
            }
        }
    }

    /** Mutable accumulator: an entry grows with its continuation lines before being frozen into a record. */
    private static final class PendingEntry {
        private final LocalDateTime timestamp;
        private final Level level;
        private final String thread;
        private final String logger;
        private final StringBuilder message = new StringBuilder();

        PendingEntry(LocalDateTime timestamp, Level level, String thread, String logger, String firstMessageLine) {
            this.timestamp = timestamp;
            this.level = level;
            this.thread = thread;
            this.logger = logger;
            this.message.append(firstMessageLine);
        }

        LogEntry toEntry() {
            return new LogEntry(timestamp, level, thread, logger, message.toString());
        }
    }
}
