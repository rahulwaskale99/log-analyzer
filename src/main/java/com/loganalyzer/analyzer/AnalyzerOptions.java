package com.loganalyzer.analyzer;

import com.loganalyzer.core.Level;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Knobs controlling what {@link LogAnalyzer} computes.
 *
 * @param topN           how many top messages to report
 * @param levels         levels included in the analysis (entries below are ignored)
 * @param burstThreshold occurrences of one normalized error within the window
 *                       required to flag a burst
 * @param burstWindow    time span within which those occurrences must happen
 */
public record AnalyzerOptions(int topN, Set<Level> levels, int burstThreshold, Duration burstWindow) {

    public static final AnalyzerOptions DEFAULT =
            new AnalyzerOptions(5, EnumSet.allOf(Level.class), 5, Duration.ofMinutes(10));

    public AnalyzerOptions {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be > 0");
        }
        if (burstThreshold < 2) {
            throw new IllegalArgumentException("burstThreshold must be >= 2");
        }
        if (burstWindow == null || burstWindow.isZero() || burstWindow.isNegative()) {
            throw new IllegalArgumentException("burstWindow must be a positive Duration");
        }
        levels = Set.copyOf(levels);
    }
}
