package com.loganalyzer.report;

import com.loganalyzer.analyzer.AnalysisResult;
import com.loganalyzer.core.Level;

import java.time.format.DateTimeFormatter;

/**
 * Renders the analysis as an aligned, human-readable terminal report with
 * bar charts drawn from plain Unicode block characters — no dependencies.
 */
public final class TextReportWriter {

    private static final int MAX_BAR_WIDTH = 30;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String write(AnalysisResult result) {
        StringBuilder out = new StringBuilder();
        out.append("Log Analysis Report\n");
        out.append("===================\n");
        out.append("Total entries: ").append(result.totalEntries()).append("\n\n");

        appendLevels(result, out);
        appendTopMessages(result, out);
        appendHourly(result, out);
        appendBursts(result, out);
        return out.toString();
    }

    private void appendLevels(AnalysisResult result, StringBuilder out) {
        out.append("Levels\n");
        out.append("------\n");
        if (result.levelCounts().isEmpty()) {
            out.append("(no entries)\n\n");
            return;
        }
        long max = result.levelCounts().stream().mapToLong(AnalysisResult.LevelCount::count).max().orElse(1);
        int labelWidth = result.levelCounts().stream()
                .mapToInt(c -> c.level().name().length()).max().orElse(5);
        for (AnalysisResult.LevelCount count : result.levelCounts()) {
            out.append(String.format("  %-" + labelWidth + "s %s %d%n",
                    count.level(),
                    bar(count.count(), max),
                    count.count()));
        }
        out.append('\n');
    }

    private void appendTopMessages(AnalysisResult result, StringBuilder out) {
        out.append("Top messages (normalized, digits/ids replaced with *)\n");
        out.append("------------------------------------------------------\n");
        if (result.topMessages().isEmpty()) {
            out.append("(nothing to report)\n\n");
            return;
        }
        long max = result.topMessages().stream().mapToLong(AnalysisResult.MessageCount::count).max().orElse(1);
        for (AnalysisResult.MessageCount message : result.topMessages()) {
            out.append(String.format("  %5dx %s%n", message.count(), bar(message.count(), max)));
            out.append(String.format("       %s%n", message.normalized()));
            if (!message.example().equals(message.normalized())) {
                out.append(String.format("       e.g. %s%n", oneLine(message.example())));
            }
        }
        out.append('\n');
    }

    private void appendHourly(AnalysisResult result, StringBuilder out) {
        out.append("Hourly histogram (ERROR+FATAL / WARN)\n");
        out.append("-------------------------------------\n");
        if (result.hourlyHistogram().isEmpty()) {
            out.append("(no WARN/ERROR entries)\n\n");
            return;
        }
        long max = result.hourlyHistogram().stream()
                .mapToLong(bucket -> bucket.errors() + bucket.warns()).max().orElse(1);
        for (AnalysisResult.HourBucket bucket : result.hourlyHistogram()) {
            long combined = bucket.errors() + bucket.warns();
            out.append(String.format("  %s  %s  err=%d warn=%d%n",
                    HOUR_FORMAT.format(bucket.hour()),
                    bar(combined, max),
                    bucket.errors(),
                    bucket.warns()));
        }
        out.append('\n');
    }

    private void appendBursts(AnalysisResult result, StringBuilder out) {
        out.append("Error bursts\n");
        out.append("------------\n");
        if (result.bursts().isEmpty()) {
            out.append("(no bursts detected)\n\n");
            return;
        }
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (AnalysisResult.Burst burst : result.bursts()) {
            out.append(String.format("  [!] %dx \"%s\"%n", burst.count(), burst.normalized()));
            out.append(String.format("      between %s and %s%n",
                    timeFormat.format(burst.firstSeen()), timeFormat.format(burst.lastSeen())));
        }
    }

    /** Scales count into a bar of at most {@link #MAX_BAR_WIDTH} block characters. */
    private String bar(long count, long max) {
        int width = (int) Math.round((double) count * MAX_BAR_WIDTH / max);
        return "█".repeat(width);
    }

    private String oneLine(String message) {
        return message.replace("\n", " | ");
    }
}
