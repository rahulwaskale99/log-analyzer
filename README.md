# log-analyzer

A zero-dependency Java 21 command-line tool that parses application log files and tells you what actually happened: level counts, the most frequent errors, an hourly histogram, and detected error bursts — as a terminal report or JSON.

Built as a focused exercise in core Java: streams, records, regex, file I/O, CLI ergonomics and hand-rolled JSON — no frameworks, no dependencies.

## Sample output

```
$ java -jar target/log-analyzer.jar sample.log

Log Analysis Report
===================
Total entries: 47

Levels
------
  ERROR ██████████ 10
  INFO  ██████████████████████████████ 31
  WARN  ████ 6

Top messages (normalized, digits/ids replaced with *)
------------------------------------------------------
      6x ████████████████████████
       payment timed out for order *
       e.g. payment timed out for order 5157
```

## Usage

```
java -jar target/log-analyzer.jar <logfile> [options]

--top N                   report N most frequent messages (default 5)
--level ERROR,WARN        analyze only these levels (default: all)
--json                    emit JSON instead of the text report
--from 2026-11-17T14:00   include entries at/after this time
--to   2026-11-17T15:00   include entries at/before this time
--burst-threshold N       occurrences of one error required to flag a burst (default 5)
--burst-window-minutes M  time span a burst must fit into (default 10)
```

Examples:

```
mvn package
java -jar target/log-analyzer.jar sample.log                     # try it on the bundled sample
java -jar target/log-analyzer.jar app.log --level ERROR --top 10
java -jar target/log-analyzer.jar app.log --json > report.json
java -jar target/log-analyzer.jar app.log --from 2026-11-17T14:00 --to 2026-11-17T15:00
```

## Design notes worth knowing

**Message normalization.** Real error messages embed ids (`Order 5150 timed out`, `Order 5151 timed out`), so raw string grouping reports hundreds of "different" errors that are the same incident. Normalization replaces UUIDs, long hex ids and decimal numbers with `*` before grouping, then keeps one real example for context. This single trick turns noise into a signal.

**Burst detection.** A burst is the same normalized error occurring N times within M minutes (configurable). The window resets after a burst is recorded, so one long-running incident can yield several bursts — which is usually what you want to see in a report.

**Streaming parser.** `LogParser.parse(Path)` returns a lazy `Stream` backed by the file reader. Entries are produced as read; multi-GB logs can be analyzed on a laptop because nothing is fully materialized. Multi-line entries (stack traces, `Caused by:` chains) are folded into the entry they belong to.

**Hand-rolled JSON.** The JSON writer escapes quotes, backslashes and control characters properly, with unit tests proving it — a nice reminder of what ` Jackson` does for us every day.

## Building

```
mvn test        # run the suite
mvn package     # build target/log-analyzer.jar (fat jar, runnable)
```

Requires JDK 21.

## Roadmap

- [ ] Multi-file / glob input (`logs/*.log`)
- [ ] Gzip-compressed input support (`.log.gz`)
- [ ] `--watch` live tail mode with rolling stats
- [ ] HTML report with embedded charts
- [ ] `--format` flag for custom log line patterns (regex)
- [ ] Presets for common formats (logback, log4j2, Spring Boot default)
- [ ] CSV export alongside JSON
- [ ] Extract slow requests (`took Xms`) into a latency report
- [ ] stdin mode for pipeline usage (`tail -f app.log | log-analyzer`)
- [ ] Docker image for one-shot analysis in CI
