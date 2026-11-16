package com.loganalyzer.report;

import com.loganalyzer.analyzer.AnalysisResult;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Renders the analysis as JSON with zero dependencies — a small hand-rolled
 * writer. The interesting part is {@link #escape(String)}: any character a
 * JSON string cannot contain literally (quotes, backslashes, control
 * characters) must be escaped, or the output becomes invalid JSON that
 * downstream tools silently choke on.
 */
public final class JsonReportWriter {

    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter();

    public String write(AnalysisResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"totalEntries\": ").append(result.totalEntries()).append(",\n");

        json.append("  \"levelCounts\": [\n");
        appendList(json, result.levelCounts(), (sb, count) -> {
            sb.append("    {\"level\": \"").append(escape(count.level().name()))
                    .append("\", \"count\": ").append(count.count()).append('}');
        });

        json.append("  \"topMessages\": [\n");
        appendList(json, result.topMessages(), (sb, message) -> {
            sb.append("    {\"normalized\": \"").append(escape(message.normalized()))
                    .append("\", \"count\": ").append(message.count())
                    .append(", \"example\": \"").append(escape(message.example())).append("\"}");
        });

        json.append("  \"hourly\": [\n");
        appendList(json, result.hourlyHistogram(), (sb, bucket) -> {
            sb.append("    {\"hour\": \"").append(HOUR_FORMAT.format(bucket.hour()))
                    .append("\", \"errors\": ").append(bucket.errors())
                    .append(", \"warns\": ").append(bucket.warns()).append('}');
        });

        json.append("  \"bursts\": [\n");
        appendList(json, result.bursts(), (sb, burst) -> {
            sb.append("    {\"message\": \"").append(escape(burst.normalized()))
                    .append("\", \"count\": ").append(burst.count())
                    .append(", \"firstSeen\": \"").append(TIMESTAMP_FORMAT.format(burst.firstSeen()))
                    .append("\", \"lastSeen\": \"").append(TIMESTAMP_FORMAT.format(burst.lastSeen()))
                    .append("\"}");
        });

        json.append("}\n");
        return json.toString();
    }

    private interface ItemRenderer<T> {
        void render(StringBuilder sb, T item);
    }

    /** Renders a JSON array with commas between items and a trailing ",\n" for the next key. */
    private <T> void appendList(StringBuilder json, java.util.List<T> items, ItemRenderer<T> renderer) {
        if (items.isEmpty()) {
            json.append("  ],\n");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            renderer.render(json, items.get(i));
            json.append(i == items.size() - 1 ? "\n" : ",\n");
        }
        json.append("  ],\n");
    }

    /**
     * Escapes a Java string for inclusion inside a JSON string literal:
     * quotes, backslash, and the control characters below U+0020.
     */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
