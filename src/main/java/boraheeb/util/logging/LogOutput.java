package boraheeb.util.logging;
// -- LogOutput Interface: ----------------------------------------------
/**
* Contract for a log output destination.
*
* <p>
*   A {@code LogOutput} receives {@link LogRecord} instances and writes them
*   to a destination such as the console, a file, a network socket, or any
*   other target.
* </p>
*
* <p>
*   Implementations determine how the record is formatted and where it is sent.
*   The interface makes no assumption about formatting — each implementation
*   owns its own {@link LogFormatter}.
* </p>
*
* <p>Implementations must be thread-safe.</p>
*
* <p>
*   <b>Contract:</b> implementations must never throw exceptions from
*   {@link #publish(LogRecord)}, {@link #flush()}, or {@link #close()}. Any
*   internal failure (I/O errors, broken connections, etc.) must be caught and
*   reported through whatever diagnostic mechanism the implementation uses,
*   so that a single failing output never disrupts the {@link Logger}, which
*   dispatches the same record to every registered output in sequence.
* </p>
*
* <p>
*   <b>Contract:</b> {@link #close()} must be idempotent — calling it more than
*   once is safe and has no additional effect. This allows a {@code LogOutput}
*   to be closed both by try-with-resources and by {@link LoggerRegistry#closeAll()}
*   without error.
* </p>
*
* <p>Example usage with try-with-resources:</p>
* <pre>{@code
*   try(LogOutput output = ConsoleLogOutput.builder().build()){
*       output.publish(record);
*   }
* }</pre>
*
* @author BoRaheeb
* @see ConsoleLogOutput
* @see FileLogOutput
* @see RollingFileLogOutput
* @see SocketLogOutput
* @see AsyncLogOutput
* @see MemoryLogOutput
**/
public interface LogOutput extends AutoCloseable{
    // -- Constants: --------------------------------------------------------
    /**
    * Default minimum level for outputs that do not configure one.
    *
    * <p>
    *   Set to {@link LogLevel#TRACE} so that, by default, an output accepts
    *   records of every level and leaves filtering as an explicit opt-in.
    *   Implementations use this as the fallback for their {@code minLevel}.
    * </p>
    *
    * <p>Kept equal to {@link Logger#DEFAULT_MIN_LEVEL} by convention — update both together.</p>
    **/
    LogLevel DEFAULT_MIN_LEVEL = LogLevel.TRACE;
    // -- Interface Methods: ------------------------------------------------
    /**
    * Publishes the given log record to this output destination.
    *
    * <p>
    *   Implementations should handle a {@code null} record gracefully — for
    *   example, by ignoring it, or by reporting the problem through whatever
    *   diagnostic mechanism the implementation uses.
    * </p>
    *
    * @param record the log record to publish
    **/
    void publish(LogRecord record);
    /**
    * Flushes any internally buffered log records to the underlying destination.
    *
    * <p>
    *   Outputs that do not buffer (for example, direct console writes) may implement
    *   this as a no-op.
    * </p>
    **/
    void flush();
    /**
    * Releases any resources held by this output (streams, connections, threads).
    *
    * <p>
    *   Implementations flush any pending output as part of closing — callers
    *   do not need to call {@link #flush()} explicitly before {@code close()}.
    *   After {@code close()} returns, subsequent calls to {@link #publish(LogRecord)}
    *   and {@link #flush()} are silently ignored.
    * </p>
    **/
    @Override
    void close();
    /**
    * Returns {@code true} if this output is open and available for publishing.
    *
    * <p>
    *   For file and socket outputs this also implies that the underlying resource
    *   (writer or connection) was successfully opened. For console outputs it
    *   reflects only whether {@link #close()} has been called.
    * </p>
    *
    * @return {@code true} if this output has not been closed and is ready to accept records, otherwise {@code false}
    **/
    boolean isOpen();
}