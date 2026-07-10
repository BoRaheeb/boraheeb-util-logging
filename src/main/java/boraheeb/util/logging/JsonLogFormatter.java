package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
// -- JsonLogFormatter Class: -------------------------------------------
/**
* A {@link LogFormatter} that formats a {@link LogRecord} as a single-line
* JSON object.
*
* <p>
*   Each record is rendered as one complete JSON object per line, suitable
*   for consumption by log aggregators (Grafana Loki, ELK stack, etc.) and
*   any system that reads structured log streams line-by-line.
* </p>
*
* <p>Example output (line-wrapped for readability):</p>
* <pre>
*   {"timestamp":"2026-05-20 15:00:32","level":"INFO","logger":"boraheeb.app",
*    "source":"UserService.java","line":204,"thread":"main",
*    "message":"User loaded successfully",
*    "fields":{"userId":"9021","role":"admin"},
*    "throwable":"java.lang.RuntimeException: msg\n\tat ..."}
* </pre>
*
* <p>
*   Each part can be independently included or excluded via the
*   {@link Builder}. No external libraries are used — JSON is built
*   manually with full Unicode escape for special characters.
* </p>
*
* <p>
*   Instances are created via the {@link Builder} or from built-in presets
*   such as {@link #MINIMAL} and {@link #DEFAULT}.
* </p>
*
* <p>
*   The date-time formatter and include flags can be changed at runtime
*   via their respective setters. This class is thread-safe.
* </p>
*
* @author BoRaheeb
* @see LogFormatter
* @see TextLogFormatter
**/
public final class JsonLogFormatter implements LogFormatter{
    // -- Constants: --------------------------------------------------------
    /** Default date-time formatter assigned to builders that do not configure one explicitly. **/
    private static final LogDateTime DEFAULT_DATE_TIME = LogDateTime.ofPreset(LogDateTimePreset.DEFAULT_PRESET);
    /** Built-in preset that includes only the level and message fields — useful for high-volume compact JSON streams. **/
    public static final JsonLogFormatter MINIMAL = builder()
        .includeTimestamp(false)
        .includeLoggerName(false)
        .includeSourceName(false)
        .includeLineNumber(false)
        .includeThreadName(false)
        .includeFields(false)
        .includeThrowable(false)
        .build();
    /** Built-in preset that includes all record parts. **/
    public static final JsonLogFormatter DEFAULT = builder().build();
    // -- Fields: -----------------------------------------------------------
    /** Date-time formatter used to render the timestamp value. **/
    private volatile LogDateTime dateTime;
    /** Whether to include the timestamp field. **/
    private volatile boolean includeTimestamp;
    /** Whether to include the level field. **/
    private volatile boolean includeLevel;
    /** Whether to include the logger name field. **/
    private volatile boolean includeLoggerName;
    /** Whether to include the source name field. **/
    private volatile boolean includeSourceName;
    /** Whether to include the source line number field. **/
    private volatile boolean includeLineNumber;
    /** Whether to include the thread name field. **/
    private volatile boolean includeThreadName;
    /** Whether to include the message field. **/
    private volatile boolean includeMessage;
    /** Whether to include the structured fields object. **/
    private volatile boolean includeFields;
    /** Whether to include the throwable field. **/
    private volatile boolean includeThrowable;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a JSON log formatter from the given builder.
    *
    * @param builder the builder containing formatter values
    **/
    private JsonLogFormatter(Builder builder){
        this.dateTime = builder.dateTime;
        this.includeTimestamp = builder.includeTimestamp;
        this.includeLevel = builder.includeLevel;
        this.includeLoggerName = builder.includeLoggerName;
        this.includeSourceName = builder.includeSourceName;
        this.includeLineNumber = builder.includeLineNumber;
        this.includeThreadName = builder.includeThreadName;
        this.includeMessage = builder.includeMessage;
        this.includeFields = builder.includeFields;
        this.includeThrowable = builder.includeThrowable;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the date-time formatter used to render the timestamp value.
    *
    * @return the active date-time formatter, never {@code null}
    **/
    public LogDateTime getDateTime(){
        return dateTime;
    }
    /**
    * Returns whether the timestamp field is included in JSON output.
    *
    * @return {@code true} if the timestamp is included, otherwise {@code false}
    **/
    public boolean isIncludeTimestamp(){
        return includeTimestamp;
    }
    /**
    * Returns whether the level field is included in JSON output.
    *
    * @return {@code true} if the level is included, otherwise {@code false}
    **/
    public boolean isIncludeLevel(){
        return includeLevel;
    }
    /**
    * Returns whether the logger name field is included in JSON output.
    *
    * @return {@code true} if the logger name is included, otherwise {@code false}
    **/
    public boolean isIncludeLoggerName(){
        return includeLoggerName;
    }
    /**
    * Returns whether the source name field is included in JSON output.
    *
    * @return {@code true} if the source name is included, otherwise {@code false}
    **/
    public boolean isIncludeSourceName(){
        return includeSourceName;
    }
    /**
    * Returns whether the source line number field is included in JSON output.
    *
    * @return {@code true} if the line number is included, otherwise {@code false}
    **/
    public boolean isIncludeLineNumber(){
        return includeLineNumber;
    }
    /**
    * Returns whether the thread name field is included in JSON output.
    *
    * @return {@code true} if the thread name is included, otherwise {@code false}
    **/
    public boolean isIncludeThreadName(){
        return includeThreadName;
    }
    /**
    * Returns whether the message field is included in JSON output.
    *
    * @return {@code true} if the message is included, otherwise {@code false}
    **/
    public boolean isIncludeMessage(){
        return includeMessage;
    }
    /**
    * Returns whether the structured fields object is included in JSON output.
    *
    * @return {@code true} if the fields object is included, otherwise {@code false}
    **/
    public boolean isIncludeFields(){
        return includeFields;
    }
    /**
    * Returns whether the throwable field is included in JSON output.
    *
    * @return {@code true} if the throwable is included, otherwise {@code false}
    **/
    public boolean isIncludeThrowable(){
        return includeThrowable;
    }
    /**
    * Returns a string representation of this formatter.
    *
    * @return a string in the form {@code JsonLogFormatter[dateTime=...]}
    **/
    @Override
    public String toString(){
        return "JsonLogFormatter[dateTime=" + dateTime.getPattern() + "]";
    }
    // -- Mutator Methods: --------------------------------------------------
    /**
    * Replaces the active date-time formatter at runtime.
    *
    * <p>If {@code dateTime} is {@code null}, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param dateTime the new date-time formatter; ignored if {@code null}
    **/
    public void setDateTime(LogDateTime dateTime){
        if(dateTime == null){
            InternalDiagnostic.warn("JsonLogFormatter.setDateTime: dateTime is null -> ignored");
            return;
        }
        this.dateTime = dateTime;
    }
    /**
    * Sets whether the timestamp field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeTimestamp(boolean include){
        includeTimestamp = include;
    }
    /**
    * Sets whether the level field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeLevel(boolean include){
        includeLevel = include;
    }
    /**
    * Sets whether the logger name field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeLoggerName(boolean include){
        includeLoggerName = include;
    }
    /**
    * Sets whether the source name field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeSourceName(boolean include){
        includeSourceName = include;
    }
    /**
    * Sets whether the source line number field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeLineNumber(boolean include){
        includeLineNumber = include;
    }
    /**
    * Sets whether the thread name field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeThreadName(boolean include){
        includeThreadName = include;
    }
    /**
    * Sets whether the message field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeMessage(boolean include){
        includeMessage = include;
    }
    /**
    * Sets whether the structured fields object is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeFields(boolean include){
        includeFields = include;
    }
    /**
    * Sets whether the throwable field is included in JSON output at runtime.
    *
    * @param include {@code true} to include, {@code false} to omit
    **/
    public void setIncludeThrowable(boolean include){
        includeThrowable = include;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link JsonLogFormatter}.
    *
    * @return a new JSON log formatter builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    /**
    * Creates a new builder pre-populated with all values from this formatter.
    *
    * <p>
    *   Use this to create a modified copy of an existing formatter without
    *   re-specifying every field from scratch. The original formatter is not modified.
    * </p>
    *
    * @return a new builder initialized from this formatter's values
    **/
    public Builder toBuilder(){
        return new Builder()
            .dateTime(dateTime)
            .includeTimestamp(includeTimestamp)
            .includeLevel(includeLevel)
            .includeLoggerName(includeLoggerName)
            .includeSourceName(includeSourceName)
            .includeLineNumber(includeLineNumber)
            .includeThreadName(includeThreadName)
            .includeMessage(includeMessage)
            .includeFields(includeFields)
            .includeThrowable(includeThrowable);
    }
    // -- LogFormatter Methods: ---------------------------------------------
    /**
    * Formats the given {@link LogRecord} as a single-line JSON object string.
    *
    * <p>If {@code record} is {@code null}, an internal diagnostic warning is emitted and an empty string is returned.</p>
    *
    * @param record the log record to format
    * @return the JSON string, never {@code null}
    **/
    @Override
    public String format(LogRecord record){
        if(record == null){
            InternalDiagnostic.warn("JsonLogFormatter.format: record is null -> returning empty string");
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        if(includeTimestamp)
            first = appendString(out, "timestamp", dateTime.format(record.getTimestamp()), first);
        if(includeLevel)
            first = appendString(out, "level", record.getLevel().getLabel(), first);
        if(includeLoggerName)
            first = appendString(out, "logger", record.getLoggerName(), first);
        if(includeSourceName)
            first = appendString(out, "source", record.getSourceName(), first);
        if(includeLineNumber && record.hasLineNumber()){
            if(!first) out.append(',');
            out.append('"').append("line").append("\":").append(record.getLineNumber());
            first = false;
        }
        if(includeThreadName)
            first = appendString(out, "thread", record.getThreadName(), first);
        if(includeMessage)
            first = appendString(out, "message", record.getMessage(), first);
        if(includeFields && !record.getFields().isEmpty()){
            if(!first) out.append(',');
            out.append('"').append("fields").append("\":{");
            boolean fieldFirst = true;
            for(Map.Entry<String, String> entry : record.getFields().entrySet())
                fieldFirst = appendString(out, entry.getKey(), entry.getValue(), fieldFirst);
            out.append('}');
            first = false;
        }
        if(includeThrowable && record.getThrowable() != null){
            StringWriter writer = new StringWriter();
            record.getThrowable().printStackTrace(new PrintWriter(writer));
            first = appendString(out, "throwable", writer.toString().trim(), first);
        }
        out.append('}');
        return out.toString();
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Appends a JSON string field ({@code "key":"value"}) to {@code out}.
    *
    * @param out the target buffer
    * @param key the field name
    * @param value the field value
    * @param first whether this is the first field (controls comma prefix)
    * @return {@code false} (always, since a field was appended)
    **/
    private static boolean appendString(StringBuilder out, String key, String value, boolean first){
        if(!first) out.append(',');
        out.append('"').append(escapeJson(key)).append("\":\"");
        out.append(escapeJson((value == null)? "null" : value));
        out.append('"');
        return false;
    }
    /**
    * Escapes a string for safe embedding in a JSON string value.
    * Handles {@code \}, {@code "}, newline, carriage return, tab, and
    * control characters (U+0000–U+001F).
    *
    * @param text the string to escape
    * @return the escaped string
    **/
    private static String escapeJson(String text){
        if(text == null) return "null";
        StringBuilder builder = new StringBuilder(text.length());
        for(int index = 0; index < text.length(); index++){
            char currentChar = text.charAt(index);
            switch(currentChar){
                case '"': builder.append("\\\""); break;
                case '\\': builder.append("\\\\"); break;
                case '\n': builder.append("\\n"); break;
                case '\r': builder.append("\\r"); break;
                case '\t': builder.append("\\t"); break;
                default:
                    if(currentChar < 0x20)
                        builder.append(String.format("\\u%04x", (int) currentChar));
                    else builder.append(currentChar);
            }
        }
        return builder.toString();
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link JsonLogFormatter} instances.
    *
    * <p>
    *   Default configuration: all parts included, timestamp formatted
    *   with {@link LogDateTimePreset#DEFAULT_PRESET}.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Date-time formatter assigned to the formatter being built. **/
        private LogDateTime dateTime = DEFAULT_DATE_TIME;
        /** Whether to include the timestamp. **/
        private boolean includeTimestamp = true;
        /** Whether to include the level. **/
        private boolean includeLevel = true;
        /** Whether to include the logger name. **/
        private boolean includeLoggerName = true;
        /** Whether to include the source name. **/
        private boolean includeSourceName = true;
        /** Whether to include the line number. **/
        private boolean includeLineNumber = true;
        /** Whether to include the thread name. **/
        private boolean includeThreadName = true;
        /** Whether to include the message. **/
        private boolean includeMessage = true;
        /** Whether to include the structured fields. **/
        private boolean includeFields = true;
        /** Whether to include the throwable. **/
        private boolean includeThrowable = true;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the date-time formatter used to render the timestamp value.
        *
        * <p>If {@code dateTime} is {@code null}, an internal diagnostic warning is emitted and the default date-time formatter is used.</p>
        *
        * @param dateTime the date-time formatter, or {@code null} to use the default date-time formatter
        * @return this builder
        **/
        public Builder dateTime(LogDateTime dateTime){
            if(dateTime == null){
                InternalDiagnostic.warn("JsonLogFormatter.Builder.dateTime: dateTime is null -> using DEFAULT_DATE_TIME=" + DEFAULT_DATE_TIME);
                dateTime = DEFAULT_DATE_TIME;
            }
            this.dateTime = dateTime;
            return this;
        }
        /**
        * Sets whether to include the timestamp field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeTimestamp(boolean include){
            includeTimestamp = include;
            return this;
        }
        /**
        * Sets whether to include the level field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeLevel(boolean include){
            includeLevel = include;
            return this;
        }
        /**
        * Sets whether to include the logger name field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeLoggerName(boolean include){
            includeLoggerName = include;
            return this;
        }
        /**
        * Sets whether to include the source name field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeSourceName(boolean include){
            includeSourceName = include;
            return this;
        }
        /**
        * Sets whether to include the source line number field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeLineNumber(boolean include){
            includeLineNumber = include;
            return this;
        }
        /**
        * Sets whether to include the thread name field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeThreadName(boolean include){
            includeThreadName = include;
            return this;
        }
        /**
        * Sets whether to include the message field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeMessage(boolean include){
            includeMessage = include;
            return this;
        }
        /**
        * Sets whether to include the structured fields object in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeFields(boolean include){
            includeFields = include;
            return this;
        }
        /**
        * Sets whether to include the throwable field in JSON output.
        *
        * @param include {@code true} to include, {@code false} to omit
        * @return this builder
        **/
        public Builder includeThrowable(boolean include){
            includeThrowable = include;
            return this;
        }
        /**
        * Builds a new {@link JsonLogFormatter}.
        *
        * @return a new JSON log formatter
        **/
        public JsonLogFormatter build(){
            return new JsonLogFormatter(this);
        }
    }
}