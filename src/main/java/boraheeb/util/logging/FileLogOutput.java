package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
// -- FileLogOutput Class: ----------------------------------------------
/**
* A {@link LogOutput} that writes log records to a file.
*
* <p>
*   Each call to {@link #publish(LogRecord)} formats the record using the
*   configured {@link LogFormatter} and appends a single line followed by a
*   newline to the target file. The file is created if it does not exist,
*   and its parent directories are created automatically.
* </p>
*
* <p>
*   The default formatter is {@link TextLogFormatter#PLAIN} — no ANSI escape
*   codes are written to the file. Pass a custom formatter to override this.
* </p>
*
* <p>
*   If the file cannot be opened at construction time, an internal diagnostic
*   error is emitted and all subsequent calls to {@link #publish(LogRecord)}
*   and {@link #flush()} become no-ops.
* </p>
*
* <p>
*   Calls are {@code synchronized} to prevent interleaved output from multiple
*   threads. {@link #close()} flushes and closes the underlying writer.
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class FileLogOutput implements LogOutput{
    // -- Constants: --------------------------------------------------------
    /** Default log file path used when none is configured. **/
    private static final Path DEFAULT_PATH = Path.of("logs", "app.log");
    // -- Fields: -----------------------------------------------------------
    /** Formatter used to convert log records into plain-text strings. **/
    private final LogFormatter formatter;
    /** Path of the target log file. **/
    private final Path path;
    /** Minimum log level required for a record to be published. **/
    private LogLevel minLevel;
    /** Optional filter applied after the level check; {@code null} means accept all. **/
    private LogFilter filter;
    /** Whether to flush after every published record. **/
    private boolean autoFlush;
    /** Buffered writer to the target file. {@code null} if the file could not be opened at construction time. **/
    private final BufferedWriter writer;
    /** Whether this output has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a file log output from the given builder.
    *
    * @param builder the builder containing output values
    **/
    private FileLogOutput(Builder builder){
        this.formatter = builder.formatter;
        this.path = builder.path;
        this.minLevel = builder.minLevel;
        this.filter = builder.filter;
        this.autoFlush = builder.autoFlush;
        this.writer = openWriter(builder.append);
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the formatter used to convert log records into strings.
    *
    * @return the active formatter, never {@code null}
    **/
    public LogFormatter getFormatter(){
        return formatter;
    }
    /**
    * Returns the path of the target log file.
    *
    * @return the log file path, never {@code null}
    **/
    public Path getPath(){
        return path;
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
    * Returns whether the writer is flushed after every published record.
    *
    * @return {@code true} if auto-flush is enabled, otherwise {@code false}
    **/
    public boolean isAutoFlush(){
        return autoFlush;
    }
    /**
    * Returns {@code true} if the file is open and this output has not been closed.
    *
    * @return {@code true} if the file is open and this output has not been closed, otherwise {@code false}
    **/
    public synchronized boolean isOpen(){
        return (!closed && writer != null);
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
            InternalDiagnostic.warn("FileLogOutput.setMinLevel: minLevel is null -> ignored");
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
    /**
    * Sets whether to flush the writer after every published record at runtime.
    *
    * @param autoFlush {@code true} to flush after every record, {@code false} to buffer
    **/
    public synchronized void setAutoFlush(boolean autoFlush){
        this.autoFlush = autoFlush;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link FileLogOutput}.
    *
    * @return a new file log output builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    // -- LogOutput Methods: ------------------------------------------------
    /**
    * Formats and appends the given log record to the target file.
    *
    * <p>
    *   Records below the configured minimum level are silently dropped.
    *   A {@code null} record is ignored with an internal diagnostic warning.
    *   Does nothing if this output has been closed or failed to open.
    * </p>
    *
    * <p>If failed to write to the file, an internal diagnostic error is emitted and the record is dropped.</p>
    *
    * @param record the log record to publish
    **/
    @Override
    public synchronized void publish(LogRecord record){
        if(closed || writer == null) return;
        if(record == null){
            InternalDiagnostic.warn("FileLogOutput.publish: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        if(filter != null && !filter.accept(record)) return;
        try{
            writer.write(formatter.format(record));
            writer.newLine();
            if(autoFlush) writer.flush();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "FileLogOutput.publish: failed to write to \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> record dropped"
            );
        }
    }
    /**
    * Flushes any buffered records to the target file.
    * Does nothing if this output has been closed or failed to open.
    *
    * <p>If failed to flush, an internal diagnostic error is emitted and pending records may be lost.</p>
    **/
    @Override
    public synchronized void flush(){
        if(closed || writer == null) return;
        try{
            writer.flush();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "FileLogOutput.flush: failed to flush \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> pending records may be lost"
            );
        }
    }
    /**
    * Flushes and closes the underlying file writer.
    *
    * <p>
    *   Calls to {@link #publish(LogRecord)} and {@link #flush()} after closing
    *   are silently ignored.
    * </p>
    *
    * <p>If failed to close, an internal diagnostic error is emitted and resources may not be released properly.</p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        closed = true;
        if(writer == null) return;
        try{
            writer.flush();
            writer.close();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "FileLogOutput.close: failed to close \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> resources may not be released properly"
            );
        }
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Opens a buffered writer to {@link #path}, creating the file and any
    * missing parent directories as needed.
    *
    * <p>If the file cannot be opened, an internal diagnostic error is emitted and {@code null} is returned.</p>
    *
    * @param append {@code true} to append to an existing file, {@code false} to overwrite
    * @return the opened writer, or {@code null} if the file could not be opened
    **/
    private BufferedWriter openWriter(boolean append){
        try{
            Path parent = path.getParent();
            if(parent != null) Files.createDirectories(parent);
            if(append)
                return Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                );
            else
                return Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                );
        }catch(IOException ex){
            InternalDiagnostic.error(
                "FileLogOutput.openWriter: failed to open file \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning null"
            );
            return null;
        }
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link FileLogOutput} instances.
    *
    * <p>
    *   Defaults: plain-text formatter (no ANSI codes) {@link TextLogFormatter#PLAIN}, {@code logs/app.log} path,
    *   minimum level {@link LogLevel#TRACE}, no filter, auto-flush enabled, and append mode enabled
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Formatter assigned to the output being built. **/
        private LogFormatter formatter = TextLogFormatter.PLAIN;
        /** Log file path assigned to the output being built. **/
        private Path path = DEFAULT_PATH;
        /** Minimum level assigned to the output being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Optional filter assigned to the output being built; {@code null} means accept all. **/
        private LogFilter filter = null;
        /** Whether to flush the writer after every published record. **/
        private boolean autoFlush = true;
        /** Whether to append to an existing file; {@code false} overwrites. **/
        private boolean append = true;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the formatter used to convert log records into strings.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default formatter is used.</p>
        *
        * @param formatter the log formatter, or {@code null} to use the default formatter
        * @return this builder
        **/
        public Builder formatter(LogFormatter formatter){
            if(formatter == null){
                InternalDiagnostic.warn("FileLogOutput.Builder.formatter: formatter is null -> using TextLogFormatter.PLAIN");
                formatter = TextLogFormatter.PLAIN;
            }
            this.formatter = formatter;
            return this;
        }
        /**
        * Sets the path of the target log file.
        * Parent directories are created automatically if they do not exist.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default path is used.</p>
        *
        * @param path the log file path, or {@code null} to use the default path
        * @return this builder
        **/
        public Builder path(Path path){
            if(path == null){
                InternalDiagnostic.warn("FileLogOutput.Builder.path: path is null -> using DEFAULT_PATH=" + DEFAULT_PATH);
                path = DEFAULT_PATH;
            }
            this.path = path;
            return this;
        }
        /**
        * Sets the path of the target log file from a string.
        * Parent directories are created automatically if they do not exist.
        *
        * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the default path is used.</p>
        *
        * @param path the log file path string, or {@code null} or blank to use the default path
        * @return this builder
        **/
        public Builder path(String path){
            if(path == null || path.isBlank()){
                InternalDiagnostic.warn("FileLogOutput.Builder.path: path string is null/blank -> using DEFAULT_PATH=" + DEFAULT_PATH);
                this.path = DEFAULT_PATH;
            }else this.path = Path.of(path.trim());
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
                InternalDiagnostic.warn("FileLogOutput.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
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
        * Sets whether to flush the writer after every published record.
        *
        * <p>
        *   When enabled, each record is immediately written to disk at the cost
        *   of reduced throughput. When disabled, records are buffered and only
        *   written when the buffer is full or {@link FileLogOutput#flush()} is called.
        * </p>
        *
        * @param autoFlush {@code true} to flush after every record, {@code false} to buffer
        * @return this builder
        **/
        public Builder autoFlush(boolean autoFlush){
            this.autoFlush = autoFlush;
            return this;
        }
        /**
        * Sets whether to append to an existing log file on open.
        * When {@code false}, the file is overwritten on open.
        *
        * @param append {@code true} to append, {@code false} to overwrite
        * @return this builder
        **/
        public Builder append(boolean append){
            this.append = append;
            return this;
        }
        /**
        * Builds a new {@link FileLogOutput}.
        *
        * @return a new file log output
        **/
        public FileLogOutput build(){
            return new FileLogOutput(this);
        }
    }
}