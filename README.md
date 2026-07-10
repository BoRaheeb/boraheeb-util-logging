# boraheeb.util.logging

A lightweight, dependency-free Java logging library for small-to-medium desktop
applications and frameworks. No SLF4J, no reflection, no external dependencies —
just `java.*` and a clean builder-based API.

## Contents

- Features
- Requirements
- Quickstart
- Outputs
- Asynchronous logging
- MDC (contextual fields)
- In-memory "flight recorder"
- `.properties` configuration
- Registry and shutdown
- Build
- Javadoc
- Performance
- Security
- Installation
- Maintainer
- License

## Features

- **Levels:** `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`.
- **Outputs:** console, file, rolling file (size/time, pruned), socket, an
  asynchronous wrapper, and an in-memory ring buffer.
- **Formatters:** styled text (themes, presets) and JSON, both with presets.
- **Filters:** by level and by logger name (exact / prefix / exclude), composable.
- **MDC:** per-thread context, merged into records, with propagation to worker
  threads.
- **Async overflow policies:** `BLOCK`, `DROP_NEWEST`, `DROP_OLDEST`, with
  operational `stats()`.
- **Configuration, three ways:** programmatic builders, runtime setters, and a
  `.properties` level layer.
- **Safe by default:** never throws from a logging call; escapes control
  characters to prevent log/terminal injection (see [SECURITY.md](SECURITY.md)).

## Requirements

- Java 21+.

## Quickstart

```java
import boraheeb.util.logging.*;

Logger log = Logger.builder("app")
    .addOutput(ConsoleLogOutput.builder().build())
    .build();

log.info("Hello {}", "world");
log.warn("low disk: {} MB left", 42);
log.error("request failed", new IllegalStateException("boom"));
```

`{}` placeholders are substituted only when the level passes, so filtered calls
cost nothing.

## Outputs

```java
// Console (defaults to System.out + styled text)
LogOutput console = ConsoleLogOutput.builder().build();

// Plain file
LogOutput file = FileLogOutput.builder()
    .path("logs/app.log")
    .build();

// Rolling file: roll at 10 MB, keep 7 files — keeps disk bounded
LogOutput rolling = RollingFileLogOutput.builder()
    .path("logs/app.log")
    .maxSizeBytes(10 * 1024 * 1024)
    .maxFiles(7)
    .build();

// JSON output (one JSON object per line)
LogOutput json = FileLogOutput.builder()
    .path("logs/app.jsonl")
    .formatter(JsonLogFormatter.DEFAULT)
    .build();

Logger log = Logger.builder("app")
    .minLevel(LogLevel.DEBUG)
    .addOutput(console)
    .addOutput(rolling)
    .build();
```

## Asynchronous logging

Wrap any output to move I/O off the calling thread. Choose how a full queue
behaves:

```java
AsyncLogOutput async = AsyncLogOutput.builder(rolling)
    .queueCapacity(8192)
    .overflowPolicy(AsyncLogOutput.OverflowPolicy.DROP_OLDEST) // or BLOCK / DROP_NEWEST
    .build();

Logger log = Logger.builder("app").addOutput(async).build();

// Operational health
AsyncLogOutput.Stats stats = async.stats();
System.out.println(stats); // accepted/delivered/dropped/deliveryFailed/blocked/pending/peakPending
```

## MDC (contextual fields)

```java
MDC.put("requestId", "REQ-8821");
try{
    log.info("payment started");   // includes requestId
}finally{
    MDC.clear();                   // always clear on thread-pool threads
}

// Carry the current context onto a worker thread
executor.submit(MDC.wrap(() -> log.info("runs with the caller's MDC")));
```

## In-memory "flight recorder"

Keep the last N records cheaply; dump them when something goes wrong.

```java
MemoryLogOutput recent = MemoryLogOutput.builder().capacity(512).build();
Logger log = Logger.builder("app").addOutput(recent).addOutput(console).build();

// ... later, on an error:
recent.dumpTo(FileLogOutput.builder().path("logs/crash-context.log").build());
List<LogRecord> snapshot = recent.snapshot(); // oldest -> newest, for a UI panel
```

## `.properties` configuration (levels)

Tune verbosity without recompiling. Keys live under `log.level.`:

```properties
log.level.root            = INFO
log.level.boraheeb.ui.*   = DEBUG   # prefix rule (segment-aware)
log.level.boraheeb.net.Client = WARN # exact rule
```

```java
LoggingConfig.load(Path.of("logging.properties"))
    .applyTo(LoggerRegistry.getInstance());
```

Resolution precedence: exact name → longest matching prefix → root. It only
adjusts loggers that already exist and never constructs outputs.

## Registry and shutdown

```java
LoggerRegistry registry = LoggerRegistry.getInstance();
registry.register(Logger.builder("app").addOutput(console).build());
Logger log = registry.getLogger("app");
```

The global registry installs a JVM shutdown hook that flushes and closes all
registered loggers. You can also close explicitly via `registry.closeAll()` or
`logger.close()`.

## Build

The Maven build (`mvn package`) handles compilation and resource copying
automatically. The commands below are for compiling the sources manually with
`javac`, without Maven — the source layout is the standard Maven one
(`src/main/java` for sources, `src/main/resources` for bundled resources).

macOS / Linux (bash):

```sh
# compile the package to ./bin
javac -d bin -cp src/main/java $(find src/main/java/boraheeb/util/logging -name '*.java')

# copy bundled resources (required by LogDateTime.printPatternSymbols() / downloadPatternSymbols())
cp -r src/main/resources/* bin/
```

Windows (PowerShell):

```powershell
# compile the package to .\bin
javac -d bin -cp src/main/java (Get-ChildItem -Recurse -Path src/main/java/boraheeb/util/logging -Filter *.java).FullName

# copy bundled resources (required by LogDateTime.printPatternSymbols() / downloadPatternSymbols())
Copy-Item -Recurse -Path src/main/resources/* -Destination bin/
```

The resource copy step is required — without it, `date-formatting-symbols.txt` is
missing from `bin`, and `LogDateTime.printPatternSymbols()` /
`downloadPatternSymbols()` will report the resource as missing at runtime. Note
that the resource must land directly under `bin/docs/date-formatting-symbols.txt`
(not `bin/resources/docs/...`) to match the classpath path the code looks up —
copying the *contents* of `src/main/resources` (as above, via `resources/*`,
not the `resources` directory itself) is what achieves that.

## Javadoc

The [`doc/`](doc/) folder contains generated API documentation — nothing in it
is hand-written, so regenerate it anytime after changing Javadoc comments.

[`theme-dracula.css`](theme-dracula.css) is an optional stylesheet bundled in
the repo. It's a small set of CSS variable overrides — not a full stylesheet —
that re-skins the generated Javadoc with a dark "Dracula" palette matching
`LogTheme.DRACULA`. It's purely cosmetic: use it or don't, whichever you
prefer. It does **not** apply automatically — `javadoc` only picks it up if
you pass `--add-stylesheet` explicitly, as shown below.

macOS / Linux (bash):

```sh
# plain (default) styling
javadoc -d doc -cp src/main/java src/main/java/boraheeb/util/logging/*.java

# with the optional Dracula theme layered on top
javadoc -d doc -cp src/main/java --add-stylesheet theme-dracula.css src/main/java/boraheeb/util/logging/*.java
```

Windows (PowerShell):

```powershell
# plain (default) styling
javadoc -d doc -cp src/main/java (Get-ChildItem -Recurse -Path src/main/java/boraheeb/util/logging -Filter *.java).FullName

# with the optional Dracula theme layered on top
javadoc -d doc -cp src/main/java --add-stylesheet theme-dracula.css (Get-ChildItem -Recurse -Path src/main/java/boraheeb/util/logging -Filter *.java).FullName
```

`--add-stylesheet` adds `theme-dracula.css` as a second stylesheet layered on
top of the default `stylesheet.css`, rather than replacing it — the default
layout and structure stay intact either way; only colors and fonts change.

## Performance

Runnable, rerunnable benchmark harnesses ship with the repository under
[`src/test/java/boraheeb/util/logging/bench/`](src/test/java/boraheeb/util/logging/bench/)
— 13 standalone `main()` programs, not JUnit tests. The numbers below are not a
claim you have to take on faith: build the project and run the harnesses
yourself.

```sh
# 1. compile production and test-scope sources (test-scope, since that's where
#    the benchmark classes live — no extra dependency is pulled in for this)
mvn -q test-compile

# 2. run any harness directly, for example:
java -cp target/classes;target/test-classes boraheeb.util.logging.bench.LoggerBenchmark
java -cp target/classes;target/test-classes boraheeb.util.logging.bench.FileBenchmark
java -cp target/classes;target/test-classes boraheeb.util.logging.bench.AsyncBenchmark
# (macOS/Linux: use ':' instead of ';' as the classpath separator)
```

The table below reflects one run of that suite (a few scenarios re-run 2–3× to
sanity-check stability) on a 6-core Windows machine, JDK 21. **Every number
here is hardware-, OS-, JVM-, disk-, and workload-dependent** — a different
core count, disk (SSD vs. network/virtualized storage), JVM version, or JIT
warm-up state can easily shift these figures by 2× or more in either direction.
Treat everything below as order-of-magnitude, not a guarantee or an SLA, and
prefer rerunning the harnesses on your own target hardware over trusting any
published table, including this one.

| Scenario | Throughput | Notes |
| --- | --- | --- |
| Level-filtered call (below threshold) | ~330,000,000 rec/s | the gate is nearly free |
| Accepted, no output | ~47,000,000 rec/s | logger path only |
| Accepted, parameterized (`{}`) | ~10,000,000 rec/s | with argument substitution |
| Buffered file, single thread | ~1.5–1.6M rec/s | end-to-end to disk |
| 16 threads → one file | ~1.3M rec/s | **zero loss verified** |

Single-thread buffered-file latency measured on that machine: **p50 ≈ 300 ns,
p90 ≈ 500 ns, p99 ≈ 5 µs, p99.9 ≈ 35–42 µs** (stable across repeated runs on
*this* machine). Latency, and especially the tail (p99/p99.9), is one of the
most hardware-sensitive numbers in this whole section — it moves with disk
type and driver, OS scheduler behavior, background system load, JVM version
and GC pauses, and how "warm" the JIT is when the measurement starts. Do not
treat the p99.9 figure above as a latency guarantee for your environment;
re-run `FileBenchmark` on your own target hardware if a specific tail-latency
number matters for your use case.

The three in-process call-path figures (filtered / no-output / parameterized)
came in noticeably higher than the previously published numbers; the disk-bound
figures (buffered file throughput and the p99.9 tail) came in lower, and with a
longer tail, than previously published. No production code changed in a way
that would plausibly affect throughput — the only production fix in this pass
was an unrelated resource-path bug in `LogDateTime` (see the Build section) —
so these deltas are attributed to running on different hardware/OS/disk than
whatever produced the original figures, not to a performance regression or
improvement in the library itself. Each figure above is from a small number of
runs on one machine, not a statistically rigorous benchmark; treat run-to-run
differences of up to ~2× as noise rather than a signal, and re-run the harness
yourself before relying on any of these numbers for capacity planning.

Stability and bounds:

- **No memory leak:** a 30-second soak (`LongRunningSoakTest`, default duration;
  pass `-DsoakSeconds=300` to reproduce the original 300-second scale) logged
  ~152M records with a *negative* net heap delta and ~264 ms of total GC pause.
- **Bounded disk:** `RollingBenchmark` writes a rerunnable, scaled-down ~300 MB
  (rather than the ~20 GB of the original claim, for a benchmark that finishes
  in a few seconds) through a rolling file and confirms on-disk usage stays
  capped near the configured bound.
- **No record loss** where guaranteed: `ConcurrencyBenchmark` verifies zero loss
  at 1/2/4/8/16 concurrent threads, and `AsyncBenchmark` verifies the `BLOCK`
  overflow policy delivers every record.
- **Async overflow trade-off:** confirmed qualitatively — `DROP_NEWEST` and
  `DROP_OLDEST` let the producer run far faster than `BLOCK` by shedding load
  instead of backpressuring the caller. `AsyncBenchmark` deliberately pairs each
  policy with an artificially slow downstream output to make the trade-off
  visible, so its absolute rec/s figures are a property of that harness's
  simulated delegate speed, not a hardware-independent constant — run it
  against your own downstream output to get a number that means something for
  your deployment.

Use `AsyncLogOutput` to move I/O off the calling thread, and
`RollingFileLogOutput` to keep disk bounded under sustained volume.

## Security

The library is safe by default (control-character neutralization, fail-soft
behavior). See [SECURITY.md](SECURITY.md) for the reporting policy and the full
security model, and the package documentation (`package-info.java`) for details.

## Installation

This project is distributed two independent ways — pick whichever fits your workflow:

```
boraheeb-util-logging
│
├── GitHub Repository
│      ├── Source Code
│      └── Releases (JAR, sources JAR, and Javadoc JAR attached per tag)
│              ├── v1.0.0
│              ├── v1.1.0
│              └── v2.0.0
│
└── Maven Central
       ├── 1.0.0
       ├── 1.1.0
       └── 2.0.0
```

### GitHub Releases (available now)

Download `boraheeb-util-logging-<version>.jar` (plus the sources and Javadoc
JARs, if wanted) directly from the
[Releases](https://github.com/BoRaheeb/boraheeb-util-logging/releases) page and
add it to your classpath or module path. No account or build tool configuration
required.

### Maven Central

Published under the verified `com.boraheeb` namespace:

```xml
<dependency>
    <groupId>com.boraheeb</groupId>
    <artifactId>boraheeb-util-logging</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Maintainer

BoRaheeb

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.