# Changelog

All notable changes to `boraheeb.util.logging` are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-07-10

Initial release.

### Added

- Core logging model: `LogLevel` (`TRACE`..`CRITICAL`), immutable `LogRecord`
  (+ builder), and `Logger` (builder-based, with `trace`/`debug`/`info`/`warn`/
  `error`/`critical` shorthands, `{}` placeholder substitution, and optional
  call-site capture via `StackWalker`).
- Flat `LoggerRegistry` (global singleton + independent instances) with a JVM
  shutdown hook that flushes and closes registered loggers.
- `MDC` (Mapped Diagnostic Context): per-thread key/value context, automatic
  merge into records, and `wrap(Runnable|Callable)` propagation to worker
  threads.
- Outputs: `ConsoleLogOutput`, `FileLogOutput`, `RollingFileLogOutput`
  (size/time-based rolling with pruning), `SocketLogOutput` (with reconnect),
  `AsyncLogOutput` (overflow policies `BLOCK` / `DROP_NEWEST` / `DROP_OLDEST`,
  with operational `Stats`), and `MemoryLogOutput` (bounded in-memory ring
  buffer / flight recorder with `snapshot()` and `dumpTo()`).
- Formatters: `TextLogFormatter` (styled, themeable, with `PLAIN`/`DEFAULT`
  presets) and `JsonLogFormatter` (single-line JSON per record), both built on
  `LogDateTime` / `LogDateTimePreset` for timestamp formatting and `LogTheme` /
  `Ansi` for styling.
- Filters: `LevelFilter` (`atLeast`/`atMost`/`exactly`/`between`) and
  `LoggerNameFilter` (`include`/`prefix`/`exclude`), composable via
  `LogFilter.and`/`or`/`negate`.
- `LoggingConfig`: `.properties`-based minimum-level configuration with exact,
  segment-aware prefix, and root fallback rules, applied via
  `LoggingConfig#applyTo(LoggerRegistry)`.
- Security-by-default behavior: control-character neutralization to prevent
  log/terminal injection, and a fail-soft contract (logging calls never throw;
  invalid input falls back to a documented default and is reported through an
  internal diagnostic channel). See `SECURITY.md` and `package-info.java`.
- `module-info.java` for JPMS consumers: exports `boraheeb.util.logging` and
  requires only `java.base` and `java.management` (the latter needed solely by
  the test-scope benchmark harnesses' GC/heap reporting, not by library code).
- CI workflow (`.github/workflows/test.yml`) that runs the full test suite on
  every push and pull request to `main`.
- Full JUnit 5 test suite (533 tests across 21 test classes) covering the
  complete public API: builder validation, null/blank handling, boundary
  values, immutability, thread safety, MDC propagation, async overflow
  policies, rolling/pruning, socket reconnect, and formatter/filter behavior.
- Thirteen standalone, rerunnable benchmark/stress harnesses under
  `src/test/java/boraheeb/util/logging/bench/` (not JUnit tests) covering
  logger call-path throughput, filters, MDC, the logger registry, formatters,
  async overflow behavior, in-memory output, concurrency, buffered file I/O,
  rolling output, socket output, a combined stress scenario, and a
  configurable-duration soak test — backing the numbers in the README's
  Performance section with code anyone can run.
- README documentation: a Build section (Maven and manual `javac`), a Javadoc
  section (including the optional `theme-dracula.css` theme), a Performance
  section backed by the runnable benchmarks above, and an Installation section
  covering how to obtain the library.
