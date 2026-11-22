package com.loganalyzer.analyzer;

import com.loganalyzer.core.Level;
import com.loganalyzer.core.LogEntry;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns a stream of log entries into an {@link AnalysisResult}.
 *
 * <p>Consumes the stream once, in a single pass, without retaining every
 * input entry. It keeps aggregate state for each distinct normalized message
 * and stores each detected burst in the result. Memory therefore grows with
 * message cardinality and burst count; streaming input does not make the full
 * analysis constant-memory for every possible log file.
 *
 * <p><strong>Message normalization</strong> is what makes the "top errors"
 * list useful: real error messages embed ids (order numbers, UUIDs, trace
 * ids), so raw string grouping would report hundreds of single-occurrence
 * "different" errors that are actually the same error. Normalization replaces
 * UUIDs, long hex ids and decimal numbers with {@code *}.
 */
public final class LogAnalyzer {

    private final AnalyzerOptions options;

    public LogAnalyzer(AnalyzerOptions options) {
        this.options = options;
    }

    public AnalysisResult analyze(Stream<LogEntry> entries) {
        Map<Level, Long> levelCounts = new EnumMap<>(Level.class);
        Map<String, GroupStats> groups = new HashMap<>();
        Map<LocalDateTime, long[]> hourly = new HashMap<>(); // {errors, warns}
        Map<String, Deque<LocalDateTime>> burstCandidates = new HashMap<>();
        List<AnalysisResult.Burst> bursts = new ArrayList<>();
        long[] total = {0};

        entries.filter(entry -> options.levels().contains(entry.level()))
                .forEach(entry -> {
                    total[0]++;

                    levelCounts.merge(entry.level(), 1L, Long::sum);

                    String normalized = normalize(entry.firstMessageLine());
                    groups.computeIfAbsent(normalized, key -> new GroupStats(entry.firstMessageLine()))
                            .count++;

                    if (entry.level() == Level.WARN) {
                        hourly.computeIfAbsent(truncateToHour(entry.timestamp()), hour -> new long[2])[1]++;
                    } else if (entry.level() == Level.ERROR || entry.level() == Level.FATAL) {
                        hourly.computeIfAbsent(truncateToHour(entry.timestamp()), hour -> new long[2])[0]++;
                        detectBurst(entry, normalized, burstCandidates, bursts);
                    }
                });

        return new AnalysisResult(
                total[0],
                sortedLevelCounts(levelCounts),
                topMessages(groups),
                sortedHourly(hourly),
                sortedBursts(bursts));
    }

    /**
     * Replaces UUIDs, long hex ids and plain numbers with {@code *}, so that
     * "Order 5150 failed" and "Order 5151 failed" group as one problem.
     */
    public static String normalize(String message) {
        return message
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "*")
                .replaceAll("\\b[0-9a-fA-F]{8,}\\b", "*")
                .replaceAll("\\b\\d+\\b", "*");
    }

    /**
     * Sliding-window burst detection: remembers recent timestamps per
     * normalized message; when {@code burstThreshold} occurrences fall inside
     * {@code burstWindow}, a burst is recorded and the window resets, so one
     * long-running incident can yield several bursts.
     */
    private void detectBurst(LogEntry entry, String normalized,
                             Map<String, Deque<LocalDateTime>> candidates, List<AnalysisResult.Burst> bursts) {
        Deque<LocalDateTime> window = candidates.computeIfAbsent(normalized, key -> new ArrayDeque<>());
        LocalDateTime oldestAllowed = entry.timestamp().minus(options.burstWindow());
        while (!window.isEmpty() && window.peekFirst().isBefore(oldestAllowed)) {
            window.pollFirst();
        }
        window.addLast(entry.timestamp());
        if (window.size() >= options.burstThreshold()) {
            bursts.add(new AnalysisResult.Burst(
                    normalized, window.size(), window.peekFirst(), entry.timestamp()));
            window.clear();
        }
    }

    private List<AnalysisResult.LevelCount> sortedLevelCounts(Map<Level, Long> levelCounts) {
        return levelCounts.entrySet().stream()
                .map(e -> new AnalysisResult.LevelCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(c -> c.level().ordinal()))
                .toList();
    }

    private List<AnalysisResult.MessageCount> topMessages(Map<String, GroupStats> groups) {
        return groups.entrySet().stream()
                .sorted(Map.Entry.<String, GroupStats>comparingByValue(
                                Comparator.comparingLong(stats -> stats.count)).reversed())
                .limit(options.topN())
                .map(e -> new AnalysisResult.MessageCount(e.getKey(), e.getValue().count, e.getValue().example))
                .toList();
    }

    private List<AnalysisResult.HourBucket> sortedHourly(Map<LocalDateTime, long[]> hourly) {
        return hourly.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new AnalysisResult.HourBucket(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private List<AnalysisResult.Burst> sortedBursts(List<AnalysisResult.Burst> bursts) {
        return bursts.stream()
                .sorted(Comparator.comparingLong(AnalysisResult.Burst::count).reversed())
                .toList();
    }

    private static LocalDateTime truncateToHour(LocalDateTime timestamp) {
        return timestamp.truncatedTo(ChronoUnit.HOURS);
    }

    private static final class GroupStats {
        private final String example;
        private long count;

        GroupStats(String example) {
            this.example = example;
        }
    }
}
