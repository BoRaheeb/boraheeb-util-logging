package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
// -- LogRecord Class: --------------------------------------------------
/**
* Represents a single immutable log event.
*
* <p>
*   A {@code LogRecord} captures all relevant information about a log entry:
*   timestamp, level, logger name, source name, line number, thread name,
*   message, optional structured fields, and an optional throwable.
* </p>
*
* <p>
*   Instances are created via the {@link Builder} using a fluent API.
*   Invalid or null inputs are handled gracefully with internal fallback
*   values, and no exceptions are thrown to callers.
* </p>
*
* <p>This class is immutable and thread-safe.</p>
*
* @author BoRaheeb
**/
public final class LogRecord{
    // -- Constants: --------------------------------------------------------
    /** Logger name used when none is provided. **/
    public static final String DEFAULT_LOGGER_NAME = "unknown-logger";
    /** Source name used when none is provided. **/
    public static final String DEFAULT_SOURCE_NAME = "unknown-source";
    /** Minimum valid line number. **/
    private static final int MIN_LINE_NUMBER = 1;
    /** Sentinel value indicating the line number is not available. **/
    private static final int UNKNOWN_LINE_NUMBER = -1;
    /** Thread name used when none is provided and the current thread name is unavailable. **/
    private static final String DEFAULT_THREAD_NAME = "unknown-thread";
    /** Message used when null or empty is provided. **/
    public static final String DEFAULT_MESSAGE = "No message";
    // -- Fields: -----------------------------------------------------------
    /** Time when this log event was created. **/
    private final Instant timestamp;
    /** Severity level of this log event. **/
    private final LogLevel level;
    /** Name of the logger that created this log event. **/
    private final String loggerName;
    /** Name of the class or source that created this log event. **/
    private final String sourceName;
    /** Source line number, or {@code -1} if unknown. **/
    private final int lineNumber;
    /** Name of the thread that created this log event. **/
    private final String threadName;
    /** Message associated with this log event. **/
    private final String message;
    /** Optional structured fields associated with this log event. **/
    private final Map<String, String> fields;
    /** Optional throwable associated with this log event. **/
    private final Throwable throwable;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a log record from the given builder.
    *
    * @param builder the builder containing log record values
    **/
    private LogRecord(Builder builder){
        this.timestamp = ((builder.timestamp != null)? builder.timestamp : Instant.now());
        this.level = builder.level;
        this.loggerName = builder.loggerName;
        this.sourceName = builder.sourceName;
        this.lineNumber = builder.lineNumber;
        String currentThread = Thread.currentThread().getName();
        this.threadName = ((builder.threadName != null && !builder.threadName.isBlank())?
            builder.threadName : ((currentThread.isBlank())? DEFAULT_THREAD_NAME : currentThread)
        );
        this.message = builder.message;
        this.fields = ((builder.fields == null || builder.fields.isEmpty())?
            Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields))
        );
        this.throwable = builder.throwable;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the time when this log event was created.
    *
    * @return the log event timestamp, never {@code null}
    **/
    public Instant getTimestamp(){
        return timestamp;
    }
    /**
    * Returns the severity level of this log event.
    *
    * @return the log level, never {@code null}
    **/
    public LogLevel getLevel(){
        return level;
    }
    /**
    * Returns the name of the logger that created this log event.
    *
    * @return the logger name, never {@code null}
    **/
    public String getLoggerName(){
        return loggerName;
    }
    /**
    * Returns the source name that created this log event.
    *
    * @return the source name, never {@code null}
    **/
    public String getSourceName(){
        return sourceName;
    }
    /**
    * Returns the source line number, or {@code -1} if unknown.
    *
    * @return the source line number, or {@code -1} if unknown
    **/
    public int getLineNumber(){
        return lineNumber;
    }
    /**
    * Returns {@code true} if a valid source line number is available.
    *
    * @return {@code true} if the line number is known, {@code false} if it is {@code -1}
    **/
    public boolean hasLineNumber(){
        return (lineNumber != UNKNOWN_LINE_NUMBER);
    }
    /**
    * Returns the name of the thread that created this log event.
    *
    * @return the thread name, never {@code null}
    **/
    public String getThreadName(){
        return threadName;
    }
    /**
    * Returns the message associated with this log event.
    *
    * @return the log message, never {@code null}
    **/
    public String getMessage(){
        return message;
    }
    /**
    * Returns the structured fields associated with this log event.
    * Values are always strings — they were converted via {@link String#valueOf} at build time.
    *
    * @return an immutable map of structured fields, never {@code null}
    **/
    public Map<String, String> getFields(){
        return fields;
    }
    /**
    * Returns the optional throwable associated with this log event.
    *
    * @return the throwable, or {@code null} if none
    **/
    public Throwable getThrowable(){
        return throwable;
    }
    /**
    * Returns a string representation of this log record.
    *
    * @return a string in the form {@code LogRecord[level=..., logger=..., message=...]}
    **/
    @Override
    public String toString(){
        return "LogRecord[level=" + level.getLabel() + ", logger=" + loggerName + ", message=" + message + "]";
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link LogRecord}.
    *
    * @return a new log record builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    /**
    * Creates a new builder pre-populated with all values from this record.
    *
    * <p>
    *   Use this to create an enriched or modified copy of an existing record
    *   without re-specifying every field from scratch — for example, to add a
    *   structured field before forwarding the record. The original record is
    *   not modified.
    * </p>
    *
    * @return a new builder initialized from this record's values
    **/
    public Builder toBuilder(){
        Builder builder = new Builder()
            .timestamp(timestamp)
            .level(level)
            .loggerName(loggerName)
            .sourceName(sourceName)
            .lineNumber(lineNumber)
            .threadName(threadName)
            .message(message)
            .throwable(throwable);
        if(!fields.isEmpty())
            builder.fields = new LinkedHashMap<>(fields);
        return builder;
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link LogRecord} instances.
    *
    * <p>
    *   Provides a fluent API to set all fields. Sensible defaults are
    *   applied for any field that is not explicitly set.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Timestamp assigned to the record being built; {@code null} means capture at build time. **/
        private Instant timestamp = null;
        /** Severity level assigned to the record being built. **/
        private LogLevel level = LogLevel.DEFAULT_LEVEL;
        /** Logger name assigned to the record being built. **/
        private String loggerName = DEFAULT_LOGGER_NAME;
        /** Source name assigned to the record being built. **/
        private String sourceName = DEFAULT_SOURCE_NAME;
        /** Line number assigned to the record being built. **/
        private int lineNumber = UNKNOWN_LINE_NUMBER;
        /** Thread name assigned to the record being built; {@code null} means capture at build time. **/
        private String threadName = null;
        /** Message assigned to the record being built. **/
        private String message = DEFAULT_MESSAGE;
        /**
        * Structured fields assigned to the record being built.
        * Values are stored as strings (converted via {@link String#valueOf} at put time).
        * Initialized lazily to avoid allocating a map when no fields are set.
        **/
        private Map<String, String> fields = null;
        /** Throwable assigned to the record being built. **/
        private Throwable throwable = null;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the timestamp of this log record.
        * Pass {@code null} to capture the current time at build.
        *
        * @param timestamp the {@link Instant} to assign, or {@code null} to capture the current time at build
        * @return this builder
        **/
        public Builder timestamp(Instant timestamp){
            this.timestamp = timestamp;
            return this;
        }
        /**
        * Sets the severity level of this log record.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default level is used.</p>
        *
        * @param level the severity level, or {@code null} to use the default level
        * @return this builder
        **/
        public Builder level(LogLevel level){
            if(level == null){
                InternalDiagnostic.warn("LogRecord.Builder.level: level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
                level = LogLevel.DEFAULT_LEVEL;
            }
            this.level = level;
            return this;
        }
        /**
        * Sets the logger name of this log record.
        *
        * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the default logger name is used.</p>
        *
        * @param name the logger name, or {@code null} or blank to use the default logger name
        * @return this builder
        **/
        public Builder loggerName(String name){
            if(name == null || name.isBlank()){
                InternalDiagnostic.warn("LogRecord.Builder.loggerName: name is null/blank -> using DEFAULT_LOGGER_NAME=" + DEFAULT_LOGGER_NAME);
                name = DEFAULT_LOGGER_NAME;
            }
            loggerName = name.trim();
            return this;
        }
        /**
        * Sets the source name of this log record.
        *
        * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the default source name is used.</p>
        *
        * @param name the source name, or {@code null} or blank to use the default source name
        * @return this builder
        **/
        public Builder sourceName(String name){
            if(name == null || name.isBlank()){
                InternalDiagnostic.warn("LogRecord.Builder.sourceName: name is null/blank -> using DEFAULT_SOURCE_NAME=" + DEFAULT_SOURCE_NAME);
                name = DEFAULT_SOURCE_NAME;
            }
            sourceName = name.trim();
            return this;
        }
        /**
        * Sets the source name of this log record from a class.
        *
        * <p>
        *   Uses the simple class name (for example, {@code "FontRegistry"} from
        *   {@code FontRegistry.class}). Use {@link #sourceName(String)} for
        *   non-Java sources such as XML files or config resources.
        * </p>
        *
        * <p>
        *   If the class is {@code null}, an internal diagnostic warning is emitted and the default source name is used.
        *   If the class is anonymous, an internal diagnostic warning is emitted and the default source name is used.
        * </p>
        *
        * @param clazz the source class, or {@code null} or anonymous to use the default source name
        * @return this builder
        **/
        public Builder sourceName(Class<?> clazz){
            if(clazz == null){
                InternalDiagnostic.warn("LogRecord.Builder.sourceName: clazz is null -> using DEFAULT_SOURCE_NAME=" + DEFAULT_SOURCE_NAME);
                sourceName = DEFAULT_SOURCE_NAME;
                return this;
            }
            if(clazz.isAnonymousClass()){
                InternalDiagnostic.warn("LogRecord.Builder.sourceName: clazz is anonymous -> using DEFAULT_SOURCE_NAME=" + DEFAULT_SOURCE_NAME);
                sourceName = DEFAULT_SOURCE_NAME;
            }else sourceName = clazz.getSimpleName();
            return this;
        }
        /**
        * Sets the source line number of this log record.
        *
        * <p>
        *   A value of {@code -1} indicates the line number is unknown.
        *   Any other value less than {@code 1} is invalid and replaced with {@code -1}.
        * </p>
        *
        * <p>If an invalid value is passed, an internal diagnostic warning is emitted and the line number is set to {@code -1}.</p>
        *
        * @param value the source line number, or less than {@code 1} or {@code -1} to indicate unknown
        * @return this builder
        **/
        public Builder lineNumber(int value){
            if(value != UNKNOWN_LINE_NUMBER && value < MIN_LINE_NUMBER){
                InternalDiagnostic.warn("LogRecord.Builder.lineNumber: value is invalid (" + value + ") -> using UNKNOWN_LINE_NUMBER=" + UNKNOWN_LINE_NUMBER);
                value = UNKNOWN_LINE_NUMBER;
            }
            lineNumber = value;
            return this;
        }
        /**
        * Sets the thread name of this log record.
        * Pass {@code null} or blank to capture the current thread name at build.
        *
        * @param name the thread name to assign, or {@code null} or blank to capture the current thread name
        * @return this builder
        **/
        public Builder threadName(String name){
            threadName = name;
            return this;
        }
        /**
        * Sets the message of this log record.
        *
        * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the default message is used.</p>
        *
        * @param text the log message, or {@code null} or blank to use the default message
        * @return this builder
        **/
        public Builder message(String text){
            if(text == null || text.isBlank()){
                InternalDiagnostic.warn("LogRecord.Builder.message: text is null/blank -> using DEFAULT_MESSAGE=" + DEFAULT_MESSAGE);
                text = DEFAULT_MESSAGE;
            }
            message = text;
            return this;
        }
        /**
        * Replaces all structured fields of this log record.
        * Each value is converted to a string via {@link String#valueOf} at call time.
        *
        * <p>If {@code null} is passed, all fields are cleared.</p>
        * <p>If any key is {@code null} or blank, an internal diagnostic warning is emitted and that field is ignored.</p>
        *
        * @param fields the structured fields map, or {@code null} to clear all fields
        * @return this builder
        **/
        public Builder fields(Map<String, Object> fields){
            if(fields == null) this.fields = null;
            else{
                this.fields = new LinkedHashMap<>();
                fields.forEach((key, value) -> {
                    if(key == null || key.isBlank())
                        InternalDiagnostic.warn("LogRecord.Builder.fields: key is null/blank -> field ignored");
                    else
                        this.fields.put(key.trim(), String.valueOf(value));
                });
            }
            return this;
        }
        /**
        * Adds a single structured field to this log record.
        *
        * <p>
        *   If the key is {@code null} or blank, an internal diagnostic warning is emitted and the field is ignored.
        *   If the value is {@code null}, it is stored as the string {@code "null"}.
        * </p>
        *
        * @param key the field key, or {@code null} or blank to ignore the field
        * @param value the field value
        * @return this builder
        **/
        public Builder field(String key, Object value){
            if(key == null || key.isBlank()){
                InternalDiagnostic.warn("LogRecord.Builder.field: key is null/blank -> field ignored");
                return this;
            }
            if(fields == null)
                fields = new LinkedHashMap<>();
            fields.put(key.trim(), String.valueOf(value));
            return this;
        }
        /**
        * Sets the throwable associated with this log record.
        *
        * <p>If {@code null} is passed, no throwable is associated with the log record.</p>
        *
        * @param throwable the throwable, or {@code null} if none
        * @return this builder
        **/
        public Builder throwable(Throwable throwable){
            this.throwable = throwable;
            return this;
        }
        /**
        * Builds a new immutable {@link LogRecord}.
        *
        * @return a new immutable log record
        **/
        public LogRecord build(){
            return new LogRecord(this);
        }
    }
}