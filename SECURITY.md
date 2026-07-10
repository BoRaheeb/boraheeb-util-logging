# Security Policy

This policy covers the `boraheeb.util.logging` logging library contained in this
project (`src/boraheeb/util/logging`).

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 1.0.0 | ✅ |

## Reporting a Vulnerability

Please report suspected vulnerabilities **privately** — do not open a public
issue, pull request, or discussion for a security problem.

- **Email (preferred):** security@boraheeb.com
- **GitHub (alternative):** You may also use GitHub Private Security Advisories for this repository.
- Include: affected file/class, a description of the issue, reproduction steps or
  a proof of concept, the impact you foresee, and any suggested remediation.
- **Response:** reports are handled on a best-effort basis. You can expect an
  acknowledgement within a few days and a status update as the issue is triaged.
- Please allow a reasonable period for a fix to be prepared before any public
  disclosure (coordinated disclosure).

## Security Model

This is a lightweight, dependency-free library that **fails soft** (it never
throws from a logging call) and is **safe by default**. The authoritative,
detailed threat model lives in the package documentation
(`src/boraheeb/util/logging/package-info.java`, rendered into the API docs).
The key points:

- **Log & terminal injection (CWE-117 / CWE-150):** `TextLogFormatter` escapes
  control characters (CR, LF, and other C0 controls including `ESC`) in the
  message, structured fields, and logger/source/thread names **by default**, so
  untrusted content cannot forge log lines or manipulate the terminal. The
  throwable stack trace is rendered verbatim. `JsonLogFormatter` escapes all
  control and JSON metacharacters and is always injection-safe.
- **No redaction:** the library does not mask or scrub sensitive data. Secrets,
  credentials, tokens, and personal data must not be passed to logging calls or
  placed in `MDC` — sanitizing content before logging is the caller's
  responsibility.
- **`SocketLogOutput` is cleartext:** records are sent over plain TCP with no
  encryption and no authentication. Use it only over loopback or a trusted
  network.
- **`FileLogOutput` is unbounded:** it can exhaust disk under high volume. Use
  `RollingFileLogOutput` with `maxSizeBytes` and `maxFiles` to keep disk usage
  bounded. There is no built-in rate limiting.
- **`AsyncLogOutput` availability trade-offs:** `BLOCK` applies backpressure and
  can stall application threads; `DROP_NEWEST` / `DROP_OLDEST` protect liveness by
  discarding records. The `stats()` snapshot (rising `pending`, `dropped`,
  `deliveryFailed`) surfaces an unhealthy pipeline.

## Scope

- **In scope:** defects in the `boraheeb.util.logging` library code that affect
  the confidentiality, integrity, or availability of an application using it as
  documented.
- **Out of scope:** misuse contrary to the documented security model — for
  example, logging secrets/PII (no redaction is provided), sending sensitive logs
  over an untrusted network via `SocketLogOutput`, or using an unbounded
  `FileLogOutput` for untrusted high-volume input.