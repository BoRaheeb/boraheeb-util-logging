package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
// -- TextLogFormatter Class: -------------------------------------------
/**
* A {@link LogFormatter} that formats a {@link LogRecord} into a styled,
* human-readable string.
*
* <p>
*   Each part of the log record (timestamp, level badge, logger name,
*   source name, line number, thread name, message, structured fields,
*   and throwable) can be independently shown or hidden and is styled
*   using the colors configured in the active {@link LogTheme}.
* </p>
*
* <p>
*   If the theme has a persistent terminal background set, it is
*   automatically re-applied after each per-part style reset so that
*   the background color is maintained throughout the formatted output.
* </p>
*
* <p>The output format for a fully enabled entry is:</p>
* <pre>
*   > [timestamp][level][loggerName][sourceName:lineNumber][threadName] message
*   key=value, key2=value2
*   ExceptionType: error message
*   at ...
* </pre>
*
* <p>
*   Instances are created via the {@link Builder} or from built-in presets
*   such as {@link #MINIMAL}, {@link #COMPACT}, {@link #DEFAULT}, {@link #PLAIN},
*   and {@link #FULL}.
* </p>
*
* <p>
*   To prevent log forging (CWE-117) and terminal escape-sequence injection
*   (CWE-150), control characters in the message, structured fields, and the
*   logger, source, and thread names are escaped by default — carriage returns,
*   line feeds, and other C0 control characters (including {@code ESC}) are
*   rendered as visible escapes so untrusted content cannot inject new log lines
*   or manipulate the terminal. The throwable stack trace is rendered verbatim,
*   since it is multi-line by nature. Disable with
*   {@link Builder#escapeControlChars(boolean)} only for fully trusted content.
* </p>
*
* <p>
*   The theme, date-time formatter, visibility flags, and control-character
*   escaping can be changed at runtime via their respective setters. This class
*   is thread-safe.
* </p>
*
* @author BoRaheeb
* @see LogFormatter
* @see JsonLogFormatter
**/
public final class TextLogFormatter implements LogFormatter{
    // -- Constants: --------------------------------------------------------
    /** Default date-time formatter assigned to builders that do not configure one explicitly. **/
    private static final LogDateTime DEFAULT_DATE_TIME = LogDateTime.ofPreset(LogDateTimePreset.DEFAULT_PRESET);
    /** Built-in preset that shows only the level badge and message with no colors. **/
    public static final TextLogFormatter MINIMAL = builder()
        .showTimestamp(false)
        .showLoggerName(false)
        .showSourceName(false)
        .showLineNumber(false)
        .showThreadName(false)
        .showFields(false)
        .showThrowable(false)
        .theme(LogTheme.NONE)
        .build();
    /** Built-in preset that shows timestamp, level badge, and message only — useful for quick terminal output. **/
    public static final TextLogFormatter COMPACT = builder()
        .showLoggerName(false)
        .showSourceName(false)
        .showLineNumber(false)
        .showThreadName(false)
        .showFields(false)
        .showThrowable(false)
        .build();
    /** Built-in preset that shows all parts with {@link LogTheme#DEFAULT_THEME} colors. **/
    public static final TextLogFormatter DEFAULT = builder().build();
    /** Built-in preset with the {@link #DEFAULT} layout but no colors — plain text for files or non-ANSI terminals. **/
    public static final TextLogFormatter PLAIN = builder()
        .theme(LogTheme.NONE)
        .build();
    /** Built-in preset that shows all parts including source name, line number, and thread name — useful for deep debugging. **/
    public static final TextLogFormatter FULL = builder()
        .showSourceName(true)
        .showLineNumber(true)
        .showThreadName(true)
        .build();
    // -- Fields: -----------------------------------------------------------
    /** Date-time formatter used to render the timestamp. **/
    private volatile LogDateTime dateTime;
    /** Whether to include the timestamp in the formatted output. **/
    private volatile boolean showTimestamp;
    /** Whether to include the level badge in the formatted output. **/
    private volatile boolean showLevel;
    /** Whether to include the logger name in the formatted output. **/
    private volatile boolean showLoggerName;
    /** Whether to include the source name in the formatted output. **/
    private volatile boolean showSourceName;
    /** Whether to include the source line number in the formatted output. **/
    private volatile boolean showLineNumber;
    /** Whether to include the thread name in the formatted output. **/
    private volatile boolean showThreadName;
    /** Whether to include the message in the formatted output. **/
    private volatile boolean showMessage;
    /** Whether to include the structured fields in the formatted output. **/
    private volatile boolean showFields;
    /** Whether to include the throwable stack trace in the formatted output. **/
    private volatile boolean showThrowable;
    /** Theme that supplies the ANSI style string for each log record part. **/
    private volatile LogTheme theme;
    /** Whether to escape control characters (including CR, LF, and ESC) in untrusted parts to prevent log injection. **/
    private volatile boolean escapeControlChars;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a text log formatter from the given builder.
    *
    * @param builder the builder containing formatter values
    **/
    private TextLogFormatter(Builder builder){
        this.dateTime = builder.dateTime;
        this.showTimestamp = builder.showTimestamp;
        this.showLevel = builder.showLevel;
        this.showLoggerName = builder.showLoggerName;
        this.showSourceName = builder.showSourceName;
        this.showLineNumber = builder.showLineNumber;
        this.showThreadName = builder.showThreadName;
        this.showMessage = builder.showMessage;
        this.showFields = builder.showFields;
        this.showThrowable = builder.showThrowable;
        this.theme = builder.theme;
        this.escapeControlChars = builder.escapeControlChars;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the date-time formatter used to render the timestamp.
    *
    * @return the active date-time formatter, never {@code null}
    **/
    public LogDateTime getDateTime(){
        return dateTime;
    }
    /**
    * Returns whether the timestamp is included in formatted output.
    *
    * @return {@code true} if the timestamp is shown, otherwise {@code false}
    **/
    public boolean isShowTimestamp(){
        return showTimestamp;
    }
    /**
    * Returns whether the level badge is included in formatted output.
    *
    * @return {@code true} if the level badge is shown, otherwise {@code false}
    **/
    public boolean isShowLevel(){
        return showLevel;
    }
    /**
    * Returns whether the logger name is included in formatted output.
    *
    * @return {@code true} if the logger name is shown, otherwise {@code false}
    **/
    public boolean isShowLoggerName(){
        return showLoggerName;
    }
    /**
    * Returns whether the source name is included in formatted output.
    *
    * @return {@code true} if the source name is shown, otherwise {@code false}
    **/
    public boolean isShowSourceName(){
        return showSourceName;
    }
    /**
    * Returns whether the source line number is included in formatted output.
    *
    * @return {@code true} if the line number is shown, otherwise {@code false}
    **/
    public boolean isShowLineNumber(){
        return showLineNumber;
    }
    /**
    * Returns whether the thread name is included in formatted output.
    *
    * @return {@code true} if the thread name is shown, otherwise {@code false}
    **/
    public boolean isShowThreadName(){
        return showThreadName;
    }
    /**
    * Returns whether the message is included in formatted output.
    *
    * @return {@code true} if the message is shown, otherwise {@code false}
    **/
    public boolean isShowMessage(){
        return showMessage;
    }
    /**
    * Returns whether the structured fields are included in formatted output.
    *
    * @return {@code true} if the fields are shown, otherwise {@code false}
    **/
    public boolean isShowFields(){
        return showFields;
    }
    /**
    * Returns whether the throwable stack trace is included in formatted output.
    *
    * @return {@code true} if the throwable is shown, otherwise {@code false}
    **/
    public boolean isShowThrowable(){
        return showThrowable;
    }
    /**
    * Returns whether control characters in untrusted parts are escaped.
    *
    * @return {@code true} if control-character escaping is enabled, otherwise {@code false}
    **/
    public boolean isEscapeControlChars(){
        return escapeControlChars;
    }
    /**
    * Returns the theme currently used by this formatter.
    *
    * @return the active log theme, never {@code null}
    **/
    public LogTheme getTheme(){
        return theme;
    }
    /**
    * Returns a string representation of this formatter.
    *
    * @return a string in the form {@code TextLogFormatter[theme=...]}
    **/
    @Override
    public String toString(){
        return "TextLogFormatter[theme=" + theme.getThemeName() + "]";
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
            InternalDiagnostic.warn("TextLogFormatter.setDateTime: dateTime is null -> ignored");
            return;
        }
        this.dateTime = dateTime;
    }
    /**
    * Sets whether the timestamp is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowTimestamp(boolean show){
        showTimestamp = show;
    }
    /**
    * Sets whether the level badge is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowLevel(boolean show){
        showLevel = show;
    }
    /**
    * Sets whether the logger name is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowLoggerName(boolean show){
        showLoggerName = show;
    }
    /**
    * Sets whether the source name is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowSourceName(boolean show){
        showSourceName = show;
    }
    /**
    * Sets whether the source line number is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowLineNumber(boolean show){
        showLineNumber = show;
    }
    /**
    * Sets whether the thread name is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowThreadName(boolean show){
        showThreadName = show;
    }
    /**
    * Sets whether the message is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowMessage(boolean show){
        showMessage = show;
    }
    /**
    * Sets whether the structured fields are included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowFields(boolean show){
        showFields = show;
    }
    /**
    * Sets whether the throwable stack trace is included in formatted output at runtime.
    *
    * @param show {@code true} to show, {@code false} to hide
    **/
    public void setShowThrowable(boolean show){
        showThrowable = show;
    }
    /**
    * Sets whether to escape control characters in untrusted parts at runtime.
    *
    * @param escape {@code true} to escape control characters, {@code false} to write parts verbatim
    **/
    public void setEscapeControlChars(boolean escape){
        escapeControlChars = escape;
    }
    /**
    * Replaces the active theme at runtime.
    *
    * <p>
    *   The swap is immediately visible to all threads. Each call to
    *   {@link #format(LogRecord)} snapshots the theme once at entry,
    *   so a single record is always formatted with one consistent theme
    *   even if {@code setTheme} is called concurrently.
    * </p>
    *
    * <p>If {@code theme} is {@code null}, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param theme the new theme to apply; ignored if {@code null}
    **/
    public void setTheme(LogTheme theme){
        if(theme == null){
            InternalDiagnostic.warn("TextLogFormatter.setTheme: theme is null -> ignored");
            return;
        }
        this.theme = theme;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link TextLogFormatter}.
    *
    * @return a new text log formatter builder
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
            .showTimestamp(showTimestamp)
            .showLevel(showLevel)
            .showLoggerName(showLoggerName)
            .showSourceName(showSourceName)
            .showLineNumber(showLineNumber)
            .showThreadName(showThreadName)
            .showMessage(showMessage)
            .showFields(showFields)
            .showThrowable(showThrowable)
            .theme(theme)
            .escapeControlChars(escapeControlChars);
    }
    // -- LogFormatter Methods: ---------------------------------------------
    /**
    * Formats the given {@link LogRecord} into a styled string.
    *
    * <p>If {@code record} is {@code null}, an internal diagnostic warning is emitted and an empty string is returned.</p>
    *
    * @param record the log record to format
    * @return the formatted log string, never {@code null}
    **/
    @Override
    public String format(LogRecord record){
        if(record == null){
            InternalDiagnostic.warn("TextLogFormatter.format: record is null -> returning empty string");
            return "";
        }
        LogTheme activeTheme = theme;
        StringBuilder out = new StringBuilder();
        out.append("> ");
        if(showTimestamp){
            out.append('[');
            appendStyled(activeTheme.getTimestampStyle(), dateTime.format(record.getTimestamp()), out, activeTheme);
            out.append(']');
        }
        if(showLevel){
            out.append('[');
            appendStyled(activeTheme.getLevelStyle(record.getLevel()), record.getLevel().getLabel(), out, activeTheme);
            out.append(']');
        }
        if(showLoggerName){
            out.append('[');
            appendStyled(activeTheme.getLoggerNameStyle(), escape(record.getLoggerName()), out, activeTheme);
            out.append(']');
        }
        boolean hasSource = showSourceName;
        boolean hasLine = (showLineNumber && record.hasLineNumber());
        if(hasSource || hasLine){
            out.append('[');
            if(hasSource)
                appendStyled(activeTheme.getSourceNameStyle(), escape(record.getSourceName()), out, activeTheme);
            if(hasSource && hasLine)
                out.append(':');
            if(hasLine)
                appendStyled(activeTheme.getLineNumberStyle(), String.valueOf(record.getLineNumber()), out, activeTheme);
            out.append(']');
        }
        if(showThreadName){
            out.append('[');
            appendStyled(activeTheme.getThreadNameStyle(), escape(record.getThreadName()), out, activeTheme);
            out.append(']');
        }
        if(showMessage){
            out.append(' ');
            appendStyled(activeTheme.getMessageStyle(), escape(record.getMessage()), out, activeTheme);
        }
        if(showFields && !record.getFields().isEmpty()){
            out.append('\n');
            boolean first = true;
            for(Map.Entry<String, String> entry : record.getFields().entrySet()){
                if(!first) out.append(", ");
                appendStyled(activeTheme.getFieldsStyle(), escape(entry.getKey() + "=" + entry.getValue()), out, activeTheme);
                first = false;
            }
        }
        if(showThrowable && record.getThrowable() != null){
            StringWriter writer = new StringWriter();
            record.getThrowable().printStackTrace(new PrintWriter(writer));
            out.append('\n');
            appendStyled(activeTheme.getThrowableStyle(), writer.toString().trim(), out, activeTheme);
        }
        return out.toString();
    }
    /**
    * Returns the persistent terminal background ANSI escape code from the
    * active {@link LogTheme}, or {@code null} if no background is configured.
    *
    * @return the terminal background escape code, or {@code null}
    **/
    @Override
    public String terminalBackground(){
        LogTheme activeTheme = theme;
        return ((activeTheme.hasTerminalBackground())? activeTheme.getTerminalBackground() : null);
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Appends styled text to {@code out}, then resets and re-applies the
    * persistent terminal background (if any) so the background is not lost.
    *
    * @param style the ANSI style string; empty means no styling is applied
    * @param text the text to append
    * @param out the target buffer
    * @param activeTheme the theme snapshot for this format call
    **/
    private static void appendStyled(String style, String text, StringBuilder out, LogTheme activeTheme){
        if(!style.isEmpty()) out.append(style);
        out.append(text);
        if(!style.isEmpty()){
            out.append(Ansi.RESET);
            if(activeTheme.hasTerminalBackground())
                out.append(activeTheme.getTerminalBackground());
        }
    }
    /**
    * Escapes control characters in the given text when control-character escaping
    * is enabled, otherwise returns the text unchanged.
    *
    * @param text the text to escape
    * @return the escaped text, or the original text if escaping is disabled
    **/
    private String escape(String text){
        return ((escapeControlChars)? neutralize(text) : text);
    }
    /**
    * Replaces control characters (U+0000–U+001F and U+007F) with visible escape
    * sequences so untrusted content cannot forge log lines (CWE-117) or inject
    * terminal escape sequences (CWE-150).
    *
    * <p>
    *   {@code \n}, {@code \r}, and {@code \t} use their familiar two-character
    *   forms; other control characters use {@code \\uXXXX}. Theme ANSI styling is
    *   applied separately and is never neutralized.
    * </p>
    *
    * @param text the text to neutralize, possibly {@code null}
    * @return the neutralized text, or the original value if it is {@code null}, empty, or has no control characters
    **/
    private static String neutralize(String text){
        if(text == null || text.isEmpty()) return text;
        boolean hasControl = false;
        for(int index = 0; index < text.length(); index++){
            char current = text.charAt(index);
            if(current < 0x20 || current == 0x7F){
                hasControl = true;
                break;
            }
        }
        if(!hasControl) return text;
        StringBuilder builder = new StringBuilder(text.length() + 8);
        for(int index = 0; index < text.length(); index++){
            char current = text.charAt(index);
            switch(current){
                case '\n': builder.append("\\n"); break;
                case '\r': builder.append("\\r"); break;
                case '\t': builder.append("\\t"); break;
                default:
                    if(current < 0x20 || current == 0x7F)
                        builder.append(String.format("\\u%04x", (int) current));
                    else builder.append(current);
            }
        }
        return builder.toString();
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link TextLogFormatter} instances.
    *
    * <p>
    *   Default configuration: timestamp, level, logger name, message, fields,
    *   and throwable are shown. Source name, line number, and thread name are
    *   hidden. Theme defaults to {@link LogTheme#DEFAULT_THEME}, timestamp is
    *   formatted with {@link LogDateTimePreset#DEFAULT_PRESET}, and control-character
    *   escaping is enabled.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Date-time formatter assigned to the formatter being built. **/
        private LogDateTime dateTime = DEFAULT_DATE_TIME;
        /** Whether to show the timestamp. **/
        private boolean showTimestamp = true;
        /** Whether to show the level badge. **/
        private boolean showLevel = true;
        /** Whether to show the logger name. **/
        private boolean showLoggerName = true;
        /** Whether to show the source name. **/
        private boolean showSourceName = false;
        /** Whether to show the source line number. **/
        private boolean showLineNumber = false;
        /** Whether to show the thread name. **/
        private boolean showThreadName = false;
        /** Whether to show the message. **/
        private boolean showMessage = true;
        /** Whether to show the structured fields. **/
        private boolean showFields = true;
        /** Whether to show the throwable stack trace. **/
        private boolean showThrowable = true;
        /** Theme assigned to the formatter being built. **/
        private LogTheme theme = LogTheme.DEFAULT_THEME;
        /** Whether to escape control characters in untrusted parts; enabled by default for safety. **/
        private boolean escapeControlChars = true;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the date-time formatter used to render the timestamp.
        *
        * <p>If {@code dateTime} is {@code null}, an internal diagnostic warning is emitted and the default date-time formatter is used.</p>
        *
        * @param dateTime the date-time formatter, or {@code null} to use the default date-time formatter
        * @return this builder
        **/
        public Builder dateTime(LogDateTime dateTime){
            if(dateTime == null){
                InternalDiagnostic.warn("TextLogFormatter.Builder.dateTime: dateTime is null -> using DEFAULT_DATE_TIME=" + DEFAULT_DATE_TIME);
                dateTime = DEFAULT_DATE_TIME;
            }
            this.dateTime = dateTime;
            return this;
        }
        /**
        * Sets whether to include the timestamp in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showTimestamp(boolean show){
            showTimestamp = show;
            return this;
        }
        /**
        * Sets whether to include the level badge in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showLevel(boolean show){
            showLevel = show;
            return this;
        }
        /**
        * Sets whether to include the logger name in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showLoggerName(boolean show){
            showLoggerName = show;
            return this;
        }
        /**
        * Sets whether to include the source name in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showSourceName(boolean show){
            showSourceName = show;
            return this;
        }
        /**
        * Sets whether to include the source line number in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showLineNumber(boolean show){
            showLineNumber = show;
            return this;
        }
        /**
        * Sets whether to include the thread name in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showThreadName(boolean show){
            showThreadName = show;
            return this;
        }
        /**
        * Sets whether to include the message in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showMessage(boolean show){
            showMessage = show;
            return this;
        }
        /**
        * Sets whether to include the structured fields in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showFields(boolean show){
            showFields = show;
            return this;
        }
        /**
        * Sets whether to include the throwable stack trace in formatted output.
        *
        * @param show {@code true} to show, {@code false} to hide
        * @return this builder
        **/
        public Builder showThrowable(boolean show){
            showThrowable = show;
            return this;
        }
        /**
        * Sets the theme that supplies ANSI styles for each log record part.
        *
        * <p>If {@code theme} is {@code null}, an internal diagnostic warning is emitted and the default theme is used.</p>
        *
        * @param theme the log theme, or {@code null} to use the default theme
        * @return this builder
        **/
        public Builder theme(LogTheme theme){
            if(theme == null){
                InternalDiagnostic.warn("TextLogFormatter.Builder.theme: theme is null -> using LogTheme.DEFAULT_THEME");
                theme = LogTheme.DEFAULT_THEME;
            }
            this.theme = theme;
            return this;
        }
        /**
        * Sets whether to escape control characters in the message, structured
        * fields, and the logger, source, and thread names.
        *
        * <p>
        *   Enabled by default. When enabled, carriage returns, line feeds, and
        *   other control characters (including {@code ESC}) in those parts are
        *   rendered as visible escapes, preventing untrusted content from forging
        *   log lines (CWE-117) or injecting terminal escape sequences (CWE-150).
        *   Disable only when the logged content is fully trusted.
        * </p>
        *
        * @param escape {@code true} to escape control characters, {@code false} to write parts verbatim
        * @return this builder
        **/
        public Builder escapeControlChars(boolean escape){
            escapeControlChars = escape;
            return this;
        }
        /**
        * Builds a new {@link TextLogFormatter}.
        *
        * @return a new text log formatter
        **/
        public TextLogFormatter build(){
            return new TextLogFormatter(this);
        }
    }
}