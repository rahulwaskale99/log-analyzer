package com.loganalyzer.analyzer;

import com.loganalyzer.core.Level;
import com.loganalyzer.core.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAnalyzerTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 17, 14, 0, 0);

    private final AnalyzerOptions options = new AnalyzerOptions(
            5, EnumSet.allOf(Level.class), 5, Duration.ofMinutes(10));
    private final LogAnalyzer analyzer = new LogAnalyzer(options);

    @Test
    void countsEntriesPerLevel() {
        AnalysisResult result = analyzer.analyze(Stream.of(
                entry(0, Level.INFO, "started"),
                entry(1, Level.INFO, "ready"),
                entry(2, Level.WARN, "slow query"),
                entry(3, Level.ERROR, "boom"),
                entry(4, Level.ERROR, "boom again")));

        assertEquals(5, result.totalEntries());
        List<Long> byLevel = result.levelCounts().stream()
                .map(AnalysisResult.LevelCount::count).toList();
        // most severe first: 2 ERROR + 1 WARN + 2 INFO
        assertEquals(List.of(2L, 1L, 2L), byLevel, "levels ordered by severity");
        assertEquals(Level.ERROR, result.levelCounts().get(0).level());
        assertEquals(Level.WARN, result.levelCounts().get(1).level());
        assertEquals(Level.INFO, result.levelCounts().get(2).level());
    }

    @Test
    void normalizationGroupsMessagesThatOnlyDifferById() {
        AnalysisResult result = analyzer.analyze(Stream.of(
                entry(0, Level.ERROR, "Order 1001 payment timed out"),
                entry(1, Level.ERROR, "Order 1002 payment timed out"),
                entry(2, Level.ERROR, "Order 1003 payment timed out"),
                entry(3, Level.ERROR, "cache miss rate above threshold")));

        AnalysisResult.MessageCount top = result.topMessages().get(0);
        assertEquals("Order * payment timed out", top.normalized());
        assertEquals(3, top.count());
        assertEquals("Order 1001 payment timed out", top.example(), "keeps a real example");
    }

    @Test
    void normalizesUuidsAndHexIds() {
        assertEquals("request * for user * failed",
                LogAnalyzer.normalize("request 3f2b1c9a-44d1-4d2e-8f0b-9c1d2e3f4a5b for user 8817 failed"));
        assertEquals("trace * ended",
                LogAnalyzer.normalize("trace deadbeef1234 ended"));
    }

    @Test
    void burstDetectedWhenThresholdReachedInsideWindow() {
        AnalysisResult result = analyzer.analyze(Stream.of(
                entry(0, Level.ERROR, "payment timed out"),
                entry(1, Level.ERROR, "payment timed out"),
                entry(2, Level.ERROR, "payment timed out"),
                entry(3, Level.ERROR, "payment timed out"),
                entry(4, Level.ERROR, "payment timed out"))); // 5 errors across 4 minutes

        assertEquals(1, result.bursts().size());
        AnalysisResult.Burst burst = result.bursts().get(0);
        assertEquals(5, burst.count());
        assertEquals(BASE.plusMinutes(0), burst.firstSeen());
        assertEquals(BASE.plusMinutes(4), burst.lastSeen());
    }

    @Test
    void spreadOutErrorsAreNotABurst() {
        AnalysisResult result = analyzer.analyze(Stream.of(
                entry(0, Level.ERROR, "payment timed out"),
                entry(11, Level.ERROR, "payment timed out"),
                entry(22, Level.ERROR, "payment timed out"),
                entry(33, Level.ERROR, "payment timed out"),
                entry(44, Level.ERROR, "payment timed out"))); // 44 minutes > 10-minute window

        assertTrue(result.bursts().isEmpty());
    }

    @Test
    void burstWindowResetsAfterRecording() {
        AnalysisResult result = analyzer.analyze(Stream.of(
                entry(0, Level.ERROR, "payment timed out"),
                entry(1, Level.ERROR, "payment timed out"),
                entry(2, Level.ERROR, "payment timed out"),
                entry(3, Level.ERROR, "payment timed out"),
                entry(4, Level.ERROR, "payment timed out"),   // first burst: 0-4 min
                entry(5, Level.ERROR, "payment timed out"),
                entry(6, Level.ERROR, "payment timed out"),
                entry(7, Level.ERROR, "payment timed out"),
                entry(8, Level.ERROR, "payment timed out"),
                entry(9, Level.ERROR, "payment timed out"))); // second burst: 5-9 min

        assertEquals(2, result.bursts().size());
        assertEquals(5, result.bursts().get(0).count());
    }

    @Test
    void levelFilterExcludesOtherLevels() {
        AnalyzerOptions errorOnly = new AnalyzerOptions(
                5, EnumSet.of(Level.ERROR), 5, Duration.ofMinutes(10));
        AnalysisResult result = new LogAnalyzer(errorOnly).analyze(Stream.of(
                entry(0, Level.INFO, "started"),
                entry(1, Level.WARN, "slow query"),
                entry(2, Level.ERROR, "boom")));

        assertEquals(1, result.totalEntries());
    }

    @Test
    void hourlyHistogramAggregatesWarnAndError() {
        AnalysisResult result = analyzer.analyze(Stream.of(
                entry(5, Level.WARN, "slow"),
                entry(15, Level.ERROR, "boom"),
                entry(20, Level.FATAL, "crash"),
                entry(65, Level.ERROR, "next hour"))); // BASE + 65 min = next hour

        assertEquals(2, result.hourlyHistogram().size());
        AnalysisResult.HourBucket firstHour = result.hourlyHistogram().get(0);
        assertEquals(2, firstHour.errors(), "ERROR + FATAL counted together");
        assertEquals(1, firstHour.warns());
    }

    private LogEntry entry(int minuteOffset, Level level, String message) {
        return new LogEntry(BASE.plusMinutes(minuteOffset), level, "main", "com.app.Test", message);
    }
}
