package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
// -- ConsoleLogOutput Class: -------------------------------------------
/**
* A {@link LogOutput} that writes log records to a {@link PrintStream},
* typically {@code System.out} or {@code System.err}.
*
* <p>
*   Each call to {@link #publish(LogRecord)} formats the record using the
*   configured {@link LogFormatter} and writes a single line followed by a
*   newline. Calls are {@code synchronized} to prevent interleaved output
*   from multiple threads.
* </p>
*
* <p>
*   If the active {@link LogTheme} has a persistent terminal background set,
*   it is applied once to the stream when this output is created, and then
*   re-applied automatically within each formatted line by the formatter.
* </p>
*
* <p>
*   {@link #close()} does not close the underlying stream — system streams
*   ({@code System.out}, {@code System.err}) must never be closed. It only
*   flushes pending output and marks this instance as closed. Any further
*   calls to {@link #publish(LogRecord)} or {@link #flush()} after closing
*   are silently ignored.
* </p>
*
* <p>
*   The target {@link PrintStream} is captured once, at build time — either the
*   stream passed to {@link Builder#stream(PrintStream)}, or {@code System.out}
*   as it existed when {@link Builder#build()} ran. A later call to
*   {@link System#setOut(PrintStream)} or {@link System#setErr(PrintStream)} does
*   <b>not</b> retarget an already-built instance; it keeps writing to the stream
*   it was built with. Callers who need to honor a redirect made after construction
*   (for example, a test harness that captures stdout) must build or rebuild the
*   output after redirecting, or pass the desired stream explicitly via
*   {@link Builder#stream(PrintStream)}.
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class ConsoleLogOutput implements LogOutput{
    // -- Fields: -----------------------------------------------------------
    /** Formatter used to convert log records into styled strings. **/
    private final LogFormatter formatter;
    /** Target stream that receives formatted log lines. **/
    private final PrintStream stream;
    /** Minimum log level required for a record to be published. **/
    private LogLevel minLevel;
    /** Optional filter applied after the level check; {@code null} means accept all. **/
    private LogFilter filter;
    /** Whether this output has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a console log output from the given builder.
    *
    * @param builder the builder containing output values
    **/
    private ConsoleLogOutput(Builder builder){
        this.formatter = builder.formatter;
        this.stream = ((builder.stream != null)? builder.stream : new PrintStream(System.out, true, StandardCharsets.UTF_8));
        this.minLevel = builder.minLevel;
        this.filter = builder.filter;
        applyTerminalBackground();
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the formatter used to convert log records into styled strings.
    *
    * @return the active formatter, never {@code null}
    **/
    public LogFormatter getFormatter(){
        return formatter;
    }
    /**
    * Returns the target stream that receives formatted log lines.
    *
    * @return the target stream, never {@code null}
    **/
    public PrintStream getStream(){
        return stream;
    }
    /**
    * Returns the minimum log level required for a record to be published.
    *
    * @return the minimum log level, never {@code null}
    **/
    public LogLevel getMinLevel(){
        return minLevel;
    }
    /**
    * Returns the optional filter applied after the level check.
    *
    * @return the filter, or {@code null} if no filter is set
    **/
    public LogFilter getFilter(){
        return filter;
    }
    /**
    * Returns {@code true} if this output has not been closed.
    *
    * @return {@code true} if this output has not been closed, otherwise {@code false}
    **/
    public boolean isOpen(){
        return (!closed);
    }
    // -- Mutator Methods: --------------------------------------------------
    /**
    * Replaces the active minimum log level at runtime.
    * Records below the new level are silently dropped on the next publish.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param minLevel the new minimum level; ignored if {@code null}
    **/
    public synchronized void setMinLevel(LogLevel minLevel){
        if(minLevel == null){
            InternalDiagnostic.warn("ConsoleLogOutput.setMinLevel: minLevel is null -> ignored");
            return;
        }
        this.minLevel = minLevel;
    }
    /**
    * Replaces the active filter at runtime.
    * Pass {@code null} to remove the filter and accept all records.
    *
    * @param filter the new filter, or {@code null} to accept all records
    **/
    public synchronized void setFilter(LogFilter filter){
        this.filter = filter;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link ConsoleLogOutput}.
    *
    * @return a new console log output builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    // -- LogOutput Methods: ------------------------------------------------
    /**
    * Formats and publishes the given log record to the configured stream.
    *
    * <p>
    *   Records below the configured minimum level are silently dropped.
    *   A {@code null} record is ignored with an internal diagnostic warning.
    *   Does nothing if this output has been closed.
    * </p>
    *
    * @param record the log record to publish
    **/
    @Override
    public synchronized void publish(LogRecord record){
        if(closed) return;
        if(record == null){
            InternalDiagnostic.warn("ConsoleLogOutput.publish: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        if(filter != null && !filter.accept(record)) return;
        stream.println(formatter.format(record));
    }
    /**
    * Flushes the underlying stream.
    * Does nothing if this output has been closed.
    **/
    @Override
    public synchronized void flush(){
        if(closed) return;
        stream.flush();
    }
    /**
    * Flushes the underlying stream and marks this output as closed.
    *
    * <p>
    *   The underlying stream is NOT closed — system streams must remain open.
    *   Calls to {@link #publish(LogRecord)} and {@link #flush()} after closing
    *   are silently ignored.
    * </p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        stream.flush();
        closed = true;
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Writes the persistent terminal background color to the stream once,
    * if the active theme has one configured.
    **/
    private void applyTerminalBackground(){
        String backgroundStyle = formatter.terminalBackground();
        if(backgroundStyle != null){
            stream.print(backgroundStyle);
            stream.flush();
        }
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link ConsoleLogOutput} instances.
    *
    * <p>
    *   Defaults: {@link TextLogFormatter#DEFAULT} formatter, {@code System.out} stream,
    *   minimum level {@link LogLevel#TRACE}, and no filter.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Formatter assigned to the output being built. **/
        private LogFormatter formatter = TextLogFormatter.DEFAULT;
        /** Target stream assigned to the output being built; {@code null} resolves to {@code System.out} at build time. **/
        private PrintStream stream = null;
        /** Minimum level assigned to the output being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Optional filter assigned to the output being built; {@code null} means accept all. **/
        private LogFilter filter = null;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the formatter used to convert log records into styled strings.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default formatter is used.</p>
        *
        * @param formatter the log formatter, or {@code null} to use the default formatter
        * @return this builder
        **/
        public Builder formatter(LogFormatter formatter){
            if(formatter == null){
                InternalDiagnostic.warn("ConsoleLogOutput.Builder.formatter: formatter is null -> using TextLogFormatter.DEFAULT");
                formatter = TextLogFormatter.DEFAULT;
            }
            this.formatter = formatter;
            return this;
        }
        /**
        * Sets the target stream.
        * Pass {@code System.err} to route log output to the error stream.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and {@code System.out} is used.</p>
        *
        * <p>
        *   Whatever stream is in effect when {@link #build()} runs — this one, or
        *   {@code System.out} if none is set — is captured once and used for the
        *   lifetime of the built output. A later {@link System#setOut(PrintStream)}
        *   or {@link System#setErr(PrintStream)} call does not retarget it; rebuild
        *   the output, or pass the desired stream here explicitly, if a redirect
        *   made after construction must be honored.
        * </p>
        *
        * @param stream the target print stream, or {@code null} to use {@code System.out}
        * @return this builder
        **/
        public Builder stream(PrintStream stream){
            if(stream == null){
                InternalDiagnostic.warn("ConsoleLogOutput.Builder.stream: stream is null -> using System.out");
            }
            this.stream = stream;
            return this;
        }
        /**
        * Sets the minimum log level required for a record to be published.
        * Records below this level are silently dropped.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default minimum level is used.</p>
        *
        * @param minLevel the minimum log level, or {@code null} to use the default minimum level
        * @return this builder
        **/
        public Builder minLevel(LogLevel minLevel){
            if(minLevel == null){
                InternalDiagnostic.warn("ConsoleLogOutput.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
                minLevel = DEFAULT_MIN_LEVEL;
            }
            this.minLevel = minLevel;
            return this;
        }
        /**
        * Sets an optional filter applied after the level check.
        * Records for which {@link LogFilter#accept(LogRecord)} returns {@code false}
        * are silently dropped. Pass {@code null} to remove a previously set filter.
        *
        * @param filter the filter to apply, or {@code null} to accept all records
        * @return this builder
        **/
        public Builder filter(LogFilter filter){
            this.filter = filter;
            return this;
        }
        /**
        * Builds a new {@link ConsoleLogOutput}.
        *
        * @return a new console log output
        **/
        public ConsoleLogOutput build(){
            return new ConsoleLogOutput(this);
        }
    }
}