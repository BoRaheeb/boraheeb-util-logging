/**
* A lightweight, dependency-free logging library for small-to-medium desktop
* applications and frameworks.
*
* <p>
*   Records are produced through a {@link boraheeb.util.logging.Logger}, formatted
*   by a {@link boraheeb.util.logging.LogFormatter}
*   ({@link boraheeb.util.logging.TextLogFormatter} or
*   {@link boraheeb.util.logging.JsonLogFormatter}), and written by one or more
*   {@link boraheeb.util.logging.LogOutput} destinations (console, file, rolling
*   file, socket, asynchronous wrapper, in-memory ring buffer). Levels, filters,
*   and per-thread context ({@link boraheeb.util.logging.MDC}) gate what is
*   emitted. Configuration is available three ways: programmatic builders, runtime
*   setters, and a {@code .properties} level layer
*   ({@link boraheeb.util.logging.LoggingConfig}).
* </p>
*
* <h2>Security considerations</h2>
*
* <p>
*   The library is designed to fail soft (it never throws from a logging call)
*   and to be safe by default, but the points below are the security-relevant
*   behaviors a caller should understand before deploying it.
* </p>
*
* <p><b>Log injection (CWE-117) and terminal escape injection (CWE-150).</b></p>
* <ul>
*   <li>
*       {@link boraheeb.util.logging.TextLogFormatter} escapes control characters
*       (carriage return, line feed, and other C0 controls including {@code ESC})
*       in the message, structured fields, and the logger, source, and thread
*       names <b>by default</b>, so untrusted content cannot forge log lines or
*       manipulate the terminal. The throwable stack trace is rendered verbatim,
*       since it is inherently multi-line. This can be disabled with
*       {@link boraheeb.util.logging.TextLogFormatter.Builder#escapeControlChars(boolean)}
*       only when the logged content is fully trusted.
*   </li>
*   <li>
*       {@link boraheeb.util.logging.JsonLogFormatter} escapes all control
*       characters and JSON metacharacters, so its output is always well-formed
*       and injection-safe.
*   </li>
* </ul>
*
* <p><b>Confidentiality of log content.</b></p>
* <ul>
*   <li>
*       The library performs <b>no redaction or masking</b>. Whatever is passed
*       to a logging call — including any secrets, credentials, tokens, or
*       personal data placed in messages, fields, or {@link boraheeb.util.logging.MDC}
*       entries — is written verbatim. Scrubbing sensitive data before logging is
*       the caller's responsibility.
*   </li>
*   <li>
*       {@link boraheeb.util.logging.SocketLogOutput} transmits records over a
*       <b>plain TCP connection with no transport encryption and no
*       authentication</b>. Treat it as cleartext: use it only over loopback or a
*       trusted network, never to send sensitive logs across an untrusted one.
*   </li>
*   <li>
*       File-based outputs create log files with the process's default
*       permissions (umask). On shared systems, restrict the log directory's
*       permissions externally if the contents are sensitive.
*   </li>
* </ul>
*
* <p><b>Availability and resource use.</b></p>
* <ul>
*   <li>
*       {@link boraheeb.util.logging.FileLogOutput} grows <b>without bound</b> —
*       a high log volume can exhaust disk space. For untrusted or high-volume
*       input use {@link boraheeb.util.logging.RollingFileLogOutput} with a
*       {@code maxSizeBytes} and {@code maxFiles} limit to keep disk usage bounded.
*       The library has no built-in rate limiting.
*   </li>
*   <li>
*       {@link boraheeb.util.logging.AsyncLogOutput} bounds its queue and offers
*       explicit overflow policies. {@code BLOCK} applies backpressure and can
*       stall application threads if the delegate cannot keep up; {@code DROP_NEWEST}
*       and {@code DROP_OLDEST} protect liveness by discarding records. A delegate
*       whose {@code publish} blocks forever (rather than returning or throwing)
*       parks the worker thread and stops delivery; the
*       {@link boraheeb.util.logging.AsyncLogOutput#stats()} snapshot
*       (rising {@code pending}) surfaces such a condition.
*   </li>
* </ul>
*
* <p>
*   Internal misuse and failures are reported to {@code System.err} through an
*   internal diagnostic channel rather than thrown, so a misconfiguration cannot
*   crash the application but is still visible to developers.
* </p>
*
* @author BoRaheeb
**/
package boraheeb.util.logging;