# log-analyzer

A Java 21 command-line tool for getting a quick picture of what is happening in an application log.

Point it at a log file and it reports log-level counts, repeated messages, activity by hour, and bursts of recurring errors. You can read the report in the terminal or write it as JSON. It has no runtime dependencies.

## Try it

Build the runnable jar:

```bash
mvn test
mvn package
```

Run it against the sample file:

```bash
java -jar target/log-analyzer.jar sample.log
```

A few useful commands:

```bash
# Show the ten most common messages
java -jar target/log-analyzer.jar app.log --top 10

# Focus on errors and warnings
java -jar target/log-analyzer.jar app.log --level ERROR,WARN

# Write JSON to a file
java -jar target/log-analyzer.jar app.log --json > report.json

# Look at one time range
java -jar target/log-analyzer.jar app.log --from 2026-11-17T14:00 --to 2026-11-17T15:00
```

## Options

| Option | What it changes |
|---|---|
| `--top N` | Number of frequent messages to show (default: 5). |
| `--level ERROR,WARN` | Only include the selected levels. |
| `--json` | Print JSON instead of the terminal report. |
| `--from ISO_DATE_TIME` | Include entries at or after this local date-time. |
| `--to ISO_DATE_TIME` | Include entries at or before this local date-time. |
| `--burst-threshold N` | Number of matching errors needed to report a burst (default: 5). |
| `--burst-window-minutes M` | Time window used for burst detection (default: 10). |
| `--help` | Print command help. |

## What it does with the logs

**Repeated messages are grouped.** IDs and numbers often make otherwise identical errors look different. The analyzer normalizes common variable parts before counting messages, while keeping an example of the original message in the report.

**Stack traces stay with their log entry.** The parser understands the configured logback-style entry format. Lines that follow an entry and do not start a new entry are treated as continuation lines, so stack traces are not counted as separate log events.

**Large files are read as a stream.** `LogParser.parse(Path)` reads entries as the stream is consumed. It does not load the whole file into a list. Close the stream with try-with-resources; the CLI already does this.

**Bursts are based on repeated normalized errors.** The threshold and time window can be changed from the command line. This is a simple way to spot a sudden run of the same failure. Burst detection processes entries in file order and does not sort or buffer them, so the input should be chronological for the time window to reflect actual event order.

## A couple of limits

The parser currently targets the logback-style format shown in the sample file. It is not a general-purpose parser for every logging layout. Input is a single file per run; globbing, compressed files, and live tailing are not implemented yet.

The date filters use local date-time values from the log lines. There is no timezone conversion.

## Build details

- Java 21
- Maven
- JUnit 5 for tests
- No runtime libraries

The code is split into parsing, analysis, and report-writing parts. That keeps the file format handling separate from the counts and the output format.
