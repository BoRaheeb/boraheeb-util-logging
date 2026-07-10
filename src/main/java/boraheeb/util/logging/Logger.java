package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
// -- Logger Class: -----------------------------------------------------
/**
* The main entry point for producing log records.
*
* <p>
*   A {@code Logger} holds a name, an optional source name, a minimum log
*   level, and a list of {@link LogOutput} destinations. Each logging call
*   builds a {@link LogRecord} and forwards it to every registered output.
* </p>
*
* <p>
*   Level filtering is applied before building the record — if the requested
*   level is below {@link #getMinLevel()}, the call returns immediately with
*   no record allocation or message formatting. Each output also applies its
*   own level filter independently.
* </p>
*
* <p>Three layers of publishing are provided:</p>
* <ul>
*   <li>{@link #log(LogRecord)}: dispatch a pre-built record as-is.</li>
*   <li>{@link #log(LogLevel, String)} and {@link #log(LogLevel, String, Throwable)}:
*       build and dispatch a record using the logger's own name and source name.</li>
*   <li>
*       {@code trace}/{@code debug}/{@code info}/{@code warn}/{@code error}/{@code critical}
*       shortcuts — single-line convenience wrappers around
*       {@link #log(LogLevel, String, Throwable)}.
*   </li>
* </ul>
*
* <p>
*   Every message method accepts SLF4J-style {@code "{}"} placeholders — for example,
*   {@code logger.info("user {} did {} in {}ms", id, action, ms)}. Arguments are
*   substituted in order, a trailing {@link Throwable} is attached to the record
*   instead of being substituted, and the message is built only when the level
*   passes {@link #getMinLevel()}.
* </p>
*
* <p>
*   Loggers with no registered outputs silently drop all records.
*   The {@link #isEnabled(LogLevel)} method lets callers skip expensive
*   message construction when the level would be filtered anyway.
* </p>
*
* <p>This class is thread-safe.</p>
*
* <p>
*   {@link #flush()} and {@link #close()} are forwarded to every registered
*   output. {@link #close()} is idempotent — subsequent calls are ignored.
* </p>
*
* <p>
*   When {@link Builder#captureLocation(boolean)} is enabled, the calling
*   class name and line number are captured automatically via a
*   {@link StackWalker} and stored in each produced record,
*   making {@code showSourceName} and {@code showLineNumber} in the
*   {@link LogFormatter} functional without any manual setup. This is
*   disabled by default due to the cost of stack walking.
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* @author BoRaheeb
**/
public final class Logger implements AutoCloseable{
    // -- Constants: --------------------------------------------------------
    /**
    * Minimum log level used when none is provided.
    *
    * <p>Kept equal to {@link LogOutput#DEFAULT_MIN_LEVEL} by convention — update both together.</p>
    **/
    public static final LogLevel DEFAULT_MIN_LEVEL = LogLevel.TRACE;
    /** Shared stack walker used to capture the call-site location when enabled. **/
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();
    // -- Fields: -----------------------------------------------------------
    /** Name of this logger, included in every record it produces. **/
    private final String name;
    /** Source name included in every record this logger produces. **/
    private final String sourceName;
    /** Minimum log level required for a record to be dispatched. **/
    private volatile LogLevel minLevel;
    /** Whether to capture the calling class name and line number for each record. **/
    private final boolean captureLocation;
    /** Unmodifiable list of outputs that receive records from this logger. **/
    private final List<LogOutput> outputs;
    /** Whether this logger has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a logger from the given builder.
    *
    * @param builder the builder containing logger values
    **/
    private Logger(Builder builder){
        this.name = builder.name;
        this.sourceName = builder.sourceName;
        this.minLevel = builder.minLevel;
        this.captureLocation = builder.captureLocation;
        this.outputs = Collections.unmodifiableList(new ArrayList<>(builder.outputs));
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the name of this logger.
    *
    * @return the logger name, never {@code null}
    **/
    public String getName(){
        return name;
    }
    /**
    * Returns the source name included in every record this logger produces.
    *
    * @return the source name, never {@code null}
    **/
    public String getSourceName(){
        return sourceName;
    }
    /**
    * Returns the minimum log level required for a record to be dispatched.
    *
    * @return the minimum log level, never {@code null}
    **/
    public LogLevel getMinLevel(){
        return minLevel;
    }
    /**
    * Returns {@code true} if this logger captures the calling class name and
    * line number automatically for each record it produces.
    *
    * @return {@code true} if call-site location capture is enabled, otherwise {@code false}
    **/
    public boolean isCaptureLocation(){
        return captureLocation;
    }
    /**
    * Returns an unmodifiable view of the outputs registered with this logger.
    *
    * @return an unmodifiable list of log outputs, never {@code null}
    **/
    public List<LogOutput> getOutputs(){
        return outputs;
    }
    /**
    * Returns {@code true} if the given level is at or above the minimum level
    * and this logger has not been closed.
    *
    * <p>
    *   Use this before building an expensive message string to avoid
    *   unnecessary work when the record would be filtered anyway.
    * </p>
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and {@code false} is returned.</p>
    *
    * @param level the log level to test, or {@code null} to return {@code false}
    * @return {@code true} if the level would be dispatched, otherwise {@code false}
    **/
    public boolean isEnabled(LogLevel level){
        if(closed) return false;
        if(level == null){
            InternalDiagnostic.warn("Logger.isEnabled: level is null -> returning false");
            return false;
        }
        return level.isAtLeast(minLevel);
    }
    /**
    * Returns {@code true} if this logger has not been closed.
    *
    * @return {@code true} if this logger is accepting records, otherwise {@code false}
    **/
    public boolean isOpen(){
        return !closed;
    }
    // -- Mutator Methods: --------------------------------------------------
    /**
    * Replaces the active minimum log level at runtime.
    * Records below the new level are silently dropped before any output is consulted.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param minLevel the new minimum level; ignored if {@code null}
    **/
    public void setMinLevel(LogLevel minLevel){
        if(minLevel == null){
            InternalDiagnostic.warn("Logger.setMinLevel: minLevel is null -> ignored");
            return;
        }
        this.minLevel = minLevel;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link Logger}.
    *
    * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the default logger name is used.</p>
    *
    * @param name the logger name, or {@code null} or blank to use the default logger name
    * @return a new logger builder
    **/
    public static Builder builder(String name){
        if(name == null || name.isBlank()){
            InternalDiagnostic.warn("Logger.builder: name is null/blank -> using DEFAULT_LOGGER_NAME=" + LogRecord.DEFAULT_LOGGER_NAME);
            name = LogRecord.DEFAULT_LOGGER_NAME;
        }
        return new Builder(name.trim());
    }
    /**
    * Creates a new builder for constructing a {@link Logger}, using the
    * fully qualified class name as the logger name.
    *
    * <p>
    *   Equivalent to {@code Logger.builder(clazz.getName())}.
    *   Use {@link #builder(String)} when the display name should differ
    *   from the class name (for example, {@code "Firebase"}, {@code "back-end"}).
    * </p>
    *
    * <p>
    *   If {@code null} is passed, an internal diagnostic warning is emitted and the default logger name is used.
    *   If the class is anonymous, an internal diagnostic warning is emitted and the default logger name is used.
    * </p>
    *
    * @param clazz the class whose name is used as the logger name, or {@code null} or anonymous to use the default logger name
    * @return a new logger builder
    **/
    public static Builder builder(Class<?> clazz){
        if(clazz == null){
            InternalDiagnostic.warn("Logger.builder: clazz is null -> using DEFAULT_LOGGER_NAME=" + LogRecord.DEFAULT_LOGGER_NAME);
            return new Builder(LogRecord.DEFAULT_LOGGER_NAME);
        }
        if(clazz.isAnonymousClass()){
            InternalDiagnostic.warn("Logger.builder: clazz is anonymous -> using DEFAULT_LOGGER_NAME=" + LogRecord.DEFAULT_LOGGER_NAME);
            return new Builder(LogRecord.DEFAULT_LOGGER_NAME);
        }
        return new Builder(clazz.getName());
    }
    // -- Log Methods: ------------------------------------------------------
    /**
    * Dispatches the given pre-built {@link LogRecord} to all registered outputs.
    *
    * <p>
    *   The record is forwarded as-is — the logger's own name and source name
    *   are not substituted. Level filtering still applies: records below
    *   {@link #getMinLevel()} are silently dropped.
    * </p>
    *
    * <p>
    *   A {@code null} record is ignored with an internal diagnostic warning.
    *   Does nothing if this logger has been closed.
    * </p>
    *
    * @param record the log record to dispatch, or {@code null} to ignore
    **/
    public void log(LogRecord record){
        if(closed) return;
        if(record == null){
            InternalDiagnostic.warn("Logger.log: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        for(LogOutput output : outputs) publishSafely(output, record);
    }
    /**
    * Builds and dispatches a log record at the given level with the given message.
    *
    * <p>
    *   The record is populated with this logger's name and source name.
    *   Does nothing if this logger has been closed or the level is below
    *   {@link #getMinLevel()}.
    * </p>
    *
    * <p>
    *   If {@code level} is {@code null}, an internal diagnostic warning is emitted and the default level is used.
    *   If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.
    * </p>
    *
    * @param level log level, or {@code null} to use the default level
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void log(LogLevel level, String message){
        dispatch("Logger.log", level, message, null);
    }
    /**
    * Builds and dispatches a log record at the given level with the given message
    * and throwable.
    *
    * <p>
    *   The record is populated with this logger's name and source name.
    *   Does nothing if this logger has been closed or the level is below
    *   {@link #getMinLevel()}.
    * </p>
    *
    * <p>
    *   If {@code level} is {@code null}, an internal diagnostic warning is emitted and the default level is used.
    *   If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.
    * </p>
    *
    * @param level log level, or {@code null} to use the default level
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable optional throwable to attach, or {@code null} for none
    **/
    public void log(LogLevel level, String message, Throwable throwable){
        dispatch("Logger.log", level, message, throwable);
    }
    /**
    * Builds and dispatches a record at the given level, substituting each
    * {@code "{}"} placeholder in {@code format} with the next argument.
    *
    * <p>
    *   If the last argument is a {@link Throwable}, it is attached to the record
    *   instead of being substituted. The message is only built when the level
    *   passes {@link #getMinLevel()}, so filtered calls perform no placeholder substitution.
    * </p>
    *
    * <p>
    *   If {@code level} is {@code null}, an internal diagnostic warning is emitted and the default level is used.
    *   If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.
    * </p>
    *
    * @param level log level, or {@code null} to use the default level
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void log(LogLevel level, String format, Object... args){
        dispatchParameterized("Logger.log", level, format, args);
    }
    /**
    * Publishes a {@link LogLevel#TRACE} record with the given message.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void trace(String message){
        dispatch("Logger.trace", LogLevel.TRACE, message, null);
    }
    /**
    * Publishes a {@link LogLevel#TRACE} record with the given message and throwable.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable the throwable to attach, or {@code null} for none
    **/
    public void trace(String message, Throwable throwable){
        dispatch("Logger.trace", LogLevel.TRACE, message, throwable);
    }
    /**
    * Publishes a {@link LogLevel#TRACE} record, substituting each {@code "{}"}
    * placeholder in {@code format} with the next argument. A trailing
    * {@link Throwable} is attached instead of substituted.
    *
    * <p>If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void trace(String format, Object... args){
        dispatchParameterized("Logger.trace", LogLevel.TRACE, format, args);
    }
    /**
    * Publishes a {@link LogLevel#DEBUG} record with the given message.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void debug(String message){
        dispatch("Logger.debug", LogLevel.DEBUG, message, null);
    }
    /**
    * Publishes a {@link LogLevel#DEBUG} record with the given message and throwable.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable the throwable to attach, or {@code null} for none
    **/
    public void debug(String message, Throwable throwable){
        dispatch("Logger.debug", LogLevel.DEBUG, message, throwable);
    }
    /**
    * Publishes a {@link LogLevel#DEBUG} record, substituting each {@code "{}"}
    * placeholder in {@code format} with the next argument. A trailing
    * {@link Throwable} is attached instead of substituted.
    *
    * <p>If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void debug(String format, Object... args){
        dispatchParameterized("Logger.debug", LogLevel.DEBUG, format, args);
    }
    /**
    * Publishes an {@link LogLevel#INFO} record with the given message.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void info(String message){
        dispatch("Logger.info", LogLevel.INFO, message, null);
    }
    /**
    * Publishes an {@link LogLevel#INFO} record with the given message and throwable.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable the throwable to attach, or {@code null} for none
    **/
    public void info(String message, Throwable throwable){
        dispatch("Logger.info", LogLevel.INFO, message, throwable);
    }
    /**
    * Publishes an {@link LogLevel#INFO} record, substituting each {@code "{}"}
    * placeholder in {@code format} with the next argument. A trailing
    * {@link Throwable} is attached instead of substituted.
    *
    * <p>If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void info(String format, Object... args){
        dispatchParameterized("Logger.info", LogLevel.INFO, format, args);
    }
    /**
    * Publishes a {@link LogLevel#WARN} record with the given message.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void warn(String message){
        dispatch("Logger.warn", LogLevel.WARN, message, null);
    }
    /**
    * Publishes a {@link LogLevel#WARN} record with the given message and throwable.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable the throwable to attach, or {@code null} for none
    **/
    public void warn(String message, Throwable throwable){
        dispatch("Logger.warn", LogLevel.WARN, message, throwable);
    }
    /**
    * Publishes a {@link LogLevel#WARN} record, substituting each {@code "{}"}
    * placeholder in {@code format} with the next argument. A trailing
    * {@link Throwable} is attached instead of substituted.
    *
    * <p>If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void warn(String format, Object... args){
        dispatchParameterized("Logger.warn", LogLevel.WARN, format, args);
    }
    /**
    * Publishes an {@link LogLevel#ERROR} record with the given message.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void error(String message){
        dispatch("Logger.error", LogLevel.ERROR, message, null);
    }
    /**
    * Publishes an {@link LogLevel#ERROR} record with the given message and throwable.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable the throwable to attach, or {@code null} for none
    **/
    public void error(String message, Throwable throwable){
        dispatch("Logger.error", LogLevel.ERROR, message, throwable);
    }
    /**
    * Publishes an {@link LogLevel#ERROR} record, substituting each {@code "{}"}
    * placeholder in {@code format} with the next argument. A trailing
    * {@link Throwable} is attached instead of substituted.
    *
    * <p>If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void error(String format, Object... args){
        dispatchParameterized("Logger.error", LogLevel.ERROR, format, args);
    }
    /**
    * Publishes a {@link LogLevel#CRITICAL} record with the given message.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    **/
    public void critical(String message){
        dispatch("Logger.critical", LogLevel.CRITICAL, message, null);
    }
    /**
    * Publishes a {@link LogLevel#CRITICAL} record with the given message and throwable.
    *
    * <p>If {@code message} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable the throwable to attach, or {@code null} for none
    **/
    public void critical(String message, Throwable throwable){
        dispatch("Logger.critical", LogLevel.CRITICAL, message, throwable);
    }
    /**
    * Publishes a {@link LogLevel#CRITICAL} record, substituting each {@code "{}"}
    * placeholder in {@code format} with the next argument. A trailing
    * {@link Throwable} is attached instead of substituted.
    *
    * <p>If {@code format} is {@code null} or blank, an internal diagnostic warning is emitted and the default message is used.</p>
    *
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the arguments substituted in order; a trailing {@link Throwable} is attached; may be {@code null}
    **/
    public void critical(String format, Object... args){
        dispatchParameterized("Logger.critical", LogLevel.CRITICAL, format, args);
    }
    // -- Lifecycle Methods: ------------------------------------------------
    /**
    * Flushes all registered outputs.
    * Does nothing if this logger has been closed.
    **/
    public void flush(){
        if(closed) return;
        for(LogOutput output : outputs) flushSafely(output);
    }
    /**
    * Closes all registered outputs and marks this logger as closed.
    *
    * <p>
    *   Subsequent calls to any logging method are silently ignored.
    *   This method is idempotent — calling it more than once has no effect.
    * </p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        closed = true;
        for(LogOutput output : outputs) closeSafely(output);
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Publishes {@code record} to {@code output}, isolating this logger and any
    * remaining outputs from a {@link LogOutput} that violates its no-throw
    * contract (for example, via a user-supplied {@link LogFilter} or
    * {@link LogFormatter} that throws).
    *
    * <p>
    *   {@link LogOutput}'s contract requires implementations to never throw
    *   from {@code publish}, {@code flush}, or {@code close} — this guard exists
    *   because that contract is enforced by convention, not by the compiler,
    *   and a violation must not propagate into application code or prevent
    *   other outputs from receiving the same record.
    * </p>
    *
    * @param output the output to publish to
    * @param record the record to publish
    **/
    private void publishSafely(LogOutput output, LogRecord record){
        try{
            output.publish(record);
        }catch(RuntimeException ex){
            InternalDiagnostic.error(
                "Logger.publishSafely: output " + output.getClass().getSimpleName() +
                " violated the LogOutput contract in publish -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> record skipped for this output"
            );
        }
    }
    /**
    * Flushes {@code output}, isolating remaining outputs from a
    * {@link LogOutput} that violates its no-throw contract.
    *
    * @param output the output to flush
    **/
    private void flushSafely(LogOutput output){
        try{
            output.flush();
        }catch(RuntimeException ex){
            InternalDiagnostic.error(
                "Logger.flushSafely: output " + output.getClass().getSimpleName() +
                " violated the LogOutput contract in flush -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> remaining outputs still flushed"
            );
        }
    }
    /**
    * Closes {@code output}, isolating remaining outputs from a
    * {@link LogOutput} that violates its no-throw contract.
    *
    * @param output the output to close
    **/
    private void closeSafely(LogOutput output){
        try{
            output.close();
        }catch(RuntimeException ex){
            InternalDiagnostic.error(
                "Logger.closeSafely: output " + output.getClass().getSimpleName() +
                " violated the LogOutput contract in close -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> remaining outputs still closed"
            );
        }
    }
    /**
    * Substitutes each {@code "{}"} in {@code pattern} with the string value of the
    * next argument, in order. Surplus placeholders are left as-is; surplus
    * arguments are ignored. Returns {@code pattern} unchanged when there are no
    * arguments.
    *
    * @param pattern the message pattern
    * @param args the substitution arguments
    * @return the substituted message
    **/
    private static String format(String pattern, Object[] args){
        if(pattern == null || args == null || args.length == 0) return pattern;
        StringBuilder out = new StringBuilder(pattern.length() + 16 * args.length);
        int arg = 0;
        int from = 0;
        while(true){
            int brace = pattern.indexOf("{}", from);
            if(brace < 0 || arg >= args.length)
                return out.append(pattern, from, pattern.length()).toString();
            out.append(pattern, from, brace).append(String.valueOf(args[arg++]));
            from = brace + 2;
        }
    }
    /**
    * Resolves a parameterized message and dispatches it at the given level.
    *
    * <p>
    *   The level is checked against {@link #getMinLevel()} before the message is
    *   built, so a filtered call performs no placeholder substitution. If the last argument is
    *   a {@link Throwable}, it is peeled off and attached to the record rather
    *   than substituted into a placeholder.
    * </p>
    *
    * <p>
    *   {@code format} validation runs after the level-filter check: a {@code null}
    *   or blank {@code format} on a call that would be filtered out anyway does not
    *   pay the internal diagnostic cost. {@code level} is validated first regardless,
    *   since the filter check itself needs a non-null level to compare against.
    * </p>
    *
    * @param operation the public logging method used for internal diagnostics
    * @param level the log level, or {@code null} to use the default level
    * @param format the message pattern with {@code "{}"} placeholders, or {@code null} or blank to use the default message
    * @param args the substitution arguments, possibly ending with a throwable
    **/
    private void dispatchParameterized(String operation, LogLevel level, String format, Object[] args){
        if(closed) return;
        if(level == null){
            InternalDiagnostic.warn(operation + ": level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            level = LogLevel.DEFAULT_LEVEL;
        }
        if(!level.isAtLeast(minLevel)) return;
        if(format == null || format.isBlank()){
            InternalDiagnostic.warn(operation + ": format is null/blank -> using DEFAULT_MESSAGE=" + LogRecord.DEFAULT_MESSAGE);
            format = LogRecord.DEFAULT_MESSAGE;
        }
        Throwable throwable = null;
        Object[] subArgs = args;
        if(args != null && args.length > 0 && args[args.length - 1] instanceof Throwable){
            throwable = (Throwable) args[args.length - 1];
            subArgs = Arrays.copyOf(args, args.length - 1);
        }
        dispatch(operation, level, format(format, subArgs), throwable);
    }
    /**
    * Builds a {@link LogRecord} from this logger's identity fields and
    * forwards it to every registered output.
    * Returns immediately if the level is filtered or this logger is closed.
    *
    * <p>
    *   When {@link #captureLocation} is enabled, a {@link StackWalker} finds the
    *   first frame outside {@link Logger}, which is always the real call-site
    *   regardless of internal delegation depth.
    * </p>
    *
    * <p>
    *   {@code message} validation runs after the level-filter check: a {@code null}
    *   or blank {@code message} on a call that would be filtered out anyway does not
    *   pay the internal diagnostic cost. {@code level} is validated first regardless,
    *   since the filter check itself needs a non-null level to compare against.
    * </p>
    *
    * @param operation the public logging method used for internal diagnostics
    * @param level the log level, or {@code null} to use the default level
    * @param message the log message, or {@code null} or blank to use the default message
    * @param throwable optional throwable to attach, or {@code null} for none
    **/
    private void dispatch(String operation, LogLevel level, String message, Throwable throwable){
        if(closed) return;
        if(level == null){
            InternalDiagnostic.warn(operation + ": level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            level = LogLevel.DEFAULT_LEVEL;
        }
        if(!level.isAtLeast(minLevel)) return;
        if(message == null || message.isBlank()){
            InternalDiagnostic.warn(operation + ": message is null/blank -> using DEFAULT_MESSAGE=" + LogRecord.DEFAULT_MESSAGE);
            message = LogRecord.DEFAULT_MESSAGE;
        }
        LogRecord.Builder builder = LogRecord.builder()
            .level(level)
            .loggerName(name)
            .sourceName(sourceName)
            .message(message)
            .throwable(throwable);
        Map<String, String> mdc = MDC.context();
        if(!mdc.isEmpty()) mdc.forEach(builder::field);
        if(captureLocation){
            String loggerClass = Logger.class.getName();
            StackWalker.StackFrame caller = STACK_WALKER.walk(frames ->
                frames.filter(frame -> !frame.getClassName().equals(loggerClass)).findFirst().orElse(null)
            );
            if(caller != null){
                builder.sourceName(caller.getClassName());
                builder.lineNumber(caller.getLineNumber());
            }
        }
        LogRecord record = builder.build();
        for(LogOutput output : outputs) publishSafely(output, record);
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link Logger} instances.
    *
    * <p>
    *   Defaults: source name equal to the logger name, minimum level
    *   {@link Logger#DEFAULT_MIN_LEVEL}, location capture disabled, and no registered outputs.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Name assigned to the logger being built. **/
        private final String name;
        /** Source name assigned to the logger being built. **/
        private String sourceName;
        /** Minimum level assigned to the logger being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Whether to capture call-site location for each record. **/
        private boolean captureLocation = false;
        /** Outputs registered with the logger being built. **/
        private final List<LogOutput> outputs = new ArrayList<>();
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with the required logger name.
        *
        * @param name logger name
        **/
        private Builder(String name){
            this.name = name;
            sourceName = this.name;
        }
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the source name included in every record this logger produces.
        * Defaults to the logger name if not set.
        *
        * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the logger name is used.</p>
        *
        * @param name the source name, or {@code null} or blank to use the logger name
        * @return this builder
        **/
        public Builder sourceName(String name){
            if(name == null || name.isBlank()){
                InternalDiagnostic.warn("Logger.Builder.sourceName: name is null/blank -> using logger name");
                name = this.name;
            }
            sourceName = name.trim();
            return this;
        }
        /**
        * Sets the minimum log level required for a record to be dispatched.
        * Records below this level are silently dropped before any output is consulted.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default minimum level is used.</p>
        *
        * @param minLevel the minimum log level, or {@code null} to use the default minimum level
        * @return this builder
        **/
        public Builder minLevel(LogLevel minLevel){
            if(minLevel == null){
                InternalDiagnostic.warn("Logger.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
                minLevel = DEFAULT_MIN_LEVEL;
            }
            this.minLevel = minLevel;
            return this;
        }
        /**
        * Sets whether to automatically capture the calling class name and line number
        * for each log record produced by this logger.
        *
        * <p>
        *   When enabled, every call to a convenience or {@code log} method captures
        *   the caller's fully-qualified class name as the source name and the source
        *   line number via a {@link StackWalker}, overriding the logger's
        *   configured source name. Records supplied via {@link Logger#log(LogRecord)}
        *   are forwarded as-is and are not affected.
        * </p>
        *
        * <p>
        *   Disabled by default — stack walking is expensive.
        *   Enable only when call-site location in log output is required.
        * </p>
        *
        * @param captureLocation {@code true} to capture call-site location, {@code false} to skip
        * @return this builder
        **/
        public Builder captureLocation(boolean captureLocation){
            this.captureLocation = captureLocation;
            return this;
        }
        /**
        * Registers an output with this logger.
        * Multiple outputs can be registered by calling this method more than once.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
        *
        * @param output the log output to register; ignored if {@code null}
        * @return this builder
        **/
        public Builder addOutput(LogOutput output){
            if(output == null){
                InternalDiagnostic.warn("Logger.Builder.addOutput: output is null -> ignored");
                return this;
            }
            outputs.add(output);
            return this;
        }
        /**
        * Builds a new {@link Logger}.
        *
        * @return a new logger
        **/
        public Logger build(){
            return new Logger(this);
        }
    }
}