package com.loganalyzer.report;

import com.loganalyzer.analyzer.AnalysisResult;
import com.loganalyzer.core.Level;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReportWriterTest {

    private final JsonReportWriter writer = new JsonReportWriter();

    @Test
    void escapesQuotesAndNewlinesInsideStringValues() {
        assertEquals("say \\\"hi\\\"", JsonReportWriter.escape("say \"hi\""));
        assertEquals("line1\\nline2\\ttabbed\\n", JsonReportWriter.escape("line1\nline2\ttabbed\n"));
        assertEquals("back\\\\slash", JsonReportWriter.escape("back\\slash"));
    }

    @Test
    void escapesControlCharactersAsUnicode() {
        String escaped = JsonReportWriter.escape("bell \u0007 here");
        assertTrue(escaped.contains("\\u0007"), () -> escaped);
        assertFalse(escaped.contains("\u0007"));
    }

    @Test
    void producesValidJsonStructure() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 17, 14, 0, 0);
        AnalysisResult result = new AnalysisResult(
                3,
                List.of(new AnalysisResult.LevelCount(Level.ERROR, 2),
                        new AnalysisResult.LevelCount(Level.INFO, 1)),
                List.of(new AnalysisResult.MessageCount("order * failed", 2, "order 5150 failed")),
                List.of(new AnalysisResult.HourBucket(base, 2, 0)),
                List.of(new AnalysisResult.Burst("payment timed out", 2, base, base.plusMinutes(1))));

        String json = writer.write(result);

        assertTrue(json.startsWith("{"));
        assertTrue(json.trim().endsWith("}"));
        assertTrue(json.contains("\"totalEntries\": 3"));
        assertTrue(json.contains("\"level\": \"ERROR\""));
        assertTrue(json.contains("\"normalized\": \"order * failed\""));
        assertTrue(json.contains("\"hour\": \"2026-11-17T14:00\""));
        assertTrue(json.contains("\"message\": \"payment timed out\""));
        // the multi-line writer layout separates items with newlines between keys, not inside strings
        assertFalse(json.contains("\"\n"));
    }

    @Test
    void escapesEmbeddedQuotesInRealMessages() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 17, 14, 0, 0);
        AnalysisResult result = new AnalysisResult(
                1,
                List.of(),
                List.of(new AnalysisResult.MessageCount("bad \"config\" ignored", 1, "bad \"config\" ignored")),
                List.of(),
                List.of());

        String json = writer.write(result);

        assertTrue(json.contains("\\\"config\\\""), () -> json);
        assertFalse(json.contains("\"bad \""));
    }
}
