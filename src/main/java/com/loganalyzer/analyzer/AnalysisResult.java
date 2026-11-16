package com.loganalyzer.analyzer;

import com.loganalyzer.core.Level;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the analyzer computed, in report-ready shape.
 *
 * @param totalEntries    entries that passed the level filter
 * @param levelCounts     count per level, sorted most-severe first
 * @param topMessages     most frequent normalized messages, highest count first
 * @param hourlyHistogram per-hour WARN and ERROR totals, chronological
 * @param bursts          detected error bursts, highest count first
 */
public record AnalysisResult(
        long totalEntries,
        List<LevelCount> levelCounts,
        List<MessageCount> topMessages,
        List<HourBucket> hourlyHistogram,
        List<Burst> bursts) {

    /** @param level the severity @param count entries seen at that level */
    public record LevelCount(Level level, long count) {
    }

    /**
     * @param normalized the message with ids replaced by {@code *}
     * @param count      how many entries share this normalized message
     * @param example    one real, un-normalized message for context
     */
    public record MessageCount(String normalized, long count, String example) {
    }

    /** @param hour start of the hour bucket @param errors ERROR+FATAL entries @param warns WARN entries */
    public record HourBucket(LocalDateTime hour, long errors, long warns) {
    }

    /**
     * @param normalized the error message that fired repeatedly
     * @param count      occurrences inside the burst window
     * @param firstSeen  first occurrence in the burst
     * @param lastSeen   last occurrence in the burst
     */
    public record Burst(String normalized, long count, LocalDateTime firstSeen, LocalDateTime lastSeen) {
    }
}
