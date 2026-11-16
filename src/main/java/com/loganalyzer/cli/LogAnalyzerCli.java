package com.loganalyzer.cli;

import com.loganalyzer.LogAnalyzerException;
import com.loganalyzer.analyzer.AnalyzerOptions;
import com.loganalyzer.analyzer.AnalysisResult;
import com.loganalyzer.analyzer.LogAnalyzer;
import com.loganalyzer.core.Level;
import com.loganalyzer.core.LogEntry;
import com.loganalyzer.parser.LogParser;
import com.loganalyzer.report.JsonReportWriter;
import com.loganalyzer.report.TextReportWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Command line entry point.
 *
 * <pre>
 * java -jar log-analyzer.jar sample.log
 * java -jar log-analyzer.jar app.log --top 10 --level ERROR,WARN
 * java -jar log-analyzer.jar app.log --json > report.json
 * java -jar log-analyzer.jar app.log --from 2026-11-17T14:00 --to 2026-11-17T15:00
 * </pre>
 *
 * Exit codes: 0 = success, 1 = runtime failure, 2 = invalid usage.
 */
public final class LogAnalyzerCli {

    private static final String USAGE = """
            Usage: java -jar log-analyzer.jar <logfile> [options]

            Options:
              --top N                   report N most frequent messages (default 5)
              --level L1,L2,...         analyze only these levels (default: all)
              --json                    emit JSON instead of a text report
              --from ISO_DATE_TIME      include entries at/after this time (e.g. 2026-11-17T14:00)
              --to ISO_DATE_TIME        include entries at/before this time
              --burst-threshold N       occurrences of one error required to flag a burst (default 5)
              --burst-window-minutes M  time span a burst must fit into (default 10)
              --help                    show this help

            Examples:
              java -jar log-analyzer.jar sample.log
              java -jar log-analyzer.jar app.log --level ERROR --top 10 --json > errors.json
            """;

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run with --help to see the usage.");
            System.exit(2);
        } catch (LogAnalyzerException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) {
        if (args.length == 0 || containsFlag(args, "--help")) {
            System.out.print(USAGE);
            return args.length == 0 ? 2 : 0;
        }

        ParsedArguments parsed = parse(args);
        Path file = Path.of(parsed.file());
        if (!Files.isReadable(file)) {
            throw new LogAnalyzerException("Cannot read log file: " + file.toAbsolutePath());
        }

        AnalyzerOptions options = new AnalyzerOptions(
                parsed.top(), parsed.levels(), parsed.burstThreshold(),
                Duration.ofMinutes(parsed.burstWindowMinutes()));
        LogAnalyzer analyzer = new LogAnalyzer(options);

        try (Stream<LogEntry> entries = new LogParser().parse(file)) {
            Stream<LogEntry> filtered = entries
                    .filter(entry -> inRange(entry.timestamp(), parsed.from(), parsed.to()));
            AnalysisResult result = analyzer.analyze(filtered);
            String report = parsed.json()
                    ? new JsonReportWriter().write(result)
                    : new TextReportWriter().write(result);
            System.out.print(report);
        }
        return 0;
    }

    private static boolean inRange(LocalDateTime timestamp, LocalDateTime from, LocalDateTime to) {
        return (from == null || !timestamp.isBefore(from))
                && (to == null || !timestamp.isAfter(to));
    }

    private static boolean containsFlag(String[] args, String flag) {
        return Stream.of(args).anyMatch(flag::equals);
    }

    private static ParsedArguments parse(String[] args) {
        String file = null;
        int top = 5;
        Set<Level> levels = EnumSet.allOf(Level.class);
        boolean json = false;
        LocalDateTime from = null;
        LocalDateTime to = null;
        int burstThreshold = 5;
        int burstWindowMinutes = 10;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--top" -> top = requireInt(args, ++i, arg);
                case "--level" -> levels = parseLevels(requireValue(args, ++i, arg));
                case "--json" -> json = true;
                case "--from" -> from = requireDateTime(requireValue(args, ++i, arg), arg);
                case "--to" -> to = requireDateTime(requireValue(args, ++i, arg), arg);
                case "--burst-threshold" -> burstThreshold = requireInt(args, ++i, arg);
                case "--burst-window-minutes" -> burstWindowMinutes = requireInt(args, ++i, arg);
                default -> {
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    if (file != null) {
                        throw new IllegalArgumentException("Multiple log files given: '" + file + "' and '" + arg + "'");
                    }
                    file = arg;
                }
            }
        }
        if (file == null) {
            throw new IllegalArgumentException("No log file given");
        }
        return new ParsedArguments(file, top, levels, json, from, to, burstThreshold, burstWindowMinutes);
    }

    private static Set<Level> parseLevels(String commaSeparated) {
        EnumSet<Level> levels = EnumSet.noneOf(Level.class);
        for (String token : commaSeparated.split(",")) {
            if (!token.isBlank()) {
                levels.add(Level.fromString(token));
            }
        }
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("--level needs at least one level, e.g. --level ERROR,WARN");
        }
        return levels;
    }

    private static int requireInt(String[] args, int index, String flag) {
        try {
            return Integer.parseInt(requireValue(args, index, flag));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " expects a number, got: " + args[index]);
        }
    }

    private static LocalDateTime requireDateTime(String value, String flag) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(flag + " expects an ISO date-time like 2026-11-17T14:00, got: " + value);
        }
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " expects a value");
        }
        return args[index];
    }

    private record ParsedArguments(String file, int top, Set<Level> levels, boolean json,
                                   LocalDateTime from, LocalDateTime to,
                                   int burstThreshold, int burstWindowMinutes) {
    }
}
