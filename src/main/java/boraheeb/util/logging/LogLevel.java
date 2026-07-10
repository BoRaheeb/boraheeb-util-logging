package boraheeb.util.logging;
// -- LogLevel Class: ---------------------------------------------------
/**
* Represents the severity level of a log entry.
*
* <p>Levels are ordered from lowest to highest severity:</p>
* <pre>
*   [TRACE(0) -> DEBUG(1) -> INFO(2) -> WARN(3) -> ERROR(4) -> CRITICAL(5)]
* </pre>
*
* <p>
*   When a minimum level is set on a {@link Logger}, only entries
*   at or above that level will be processed.
* </p>
*
* <p>This class is immutable and thread-safe.</p>
*
* @author BoRaheeb
**/
public final class LogLevel{
    // -- Constants: --------------------------------------------------------
    // Note: each severity value below must stay aligned with this level's position
    // in the VALUES array further down — if one changes, update the other. See the
    // invariant documented on VALUES for details.
    /** Very detailed trace info. Lowest level. **/
    public static final LogLevel TRACE = new LogLevel("TRACE", 0);
    /** Debug details for developers. **/
    public static final LogLevel DEBUG = new LogLevel("DEBUG", 1);
    /** Normal application events. **/
    public static final LogLevel INFO = new LogLevel("INFO", 2);
    /** Something unexpected, but application can continue. **/
    public static final LogLevel WARN = new LogLevel("WARN", 3);
    /** Error happened. Application keeps running. **/
    public static final LogLevel ERROR = new LogLevel("ERROR", 4);
    /** Critical error. Application may be unable to continue safely. **/
    public static final LogLevel CRITICAL = new LogLevel("CRITICAL", 5);
    /** Default level, used as a fallback for null or invalid input. **/
    public static final LogLevel DEFAULT_LEVEL = INFO;
    /**
    * All supported log levels.
    *
    * <p>
    *   <b>Invariant:</b> each level's array index here must equal the {@code severity}
    *   value it was constructed with above ({@code TRACE}=0 at index 0, ..., {@code CRITICAL}=5
    *   at index 5). {@link #fromSeverity(int)} indexes directly into this array
    *   ({@code VALUES[severity]}) rather than searching it, so reordering this array or
    *   changing a level's severity without updating the other would make
    *   {@code fromSeverity} silently return the wrong level for valid-looking input.
    * </p>
    **/
    private static final LogLevel[] VALUES = {
        TRACE, DEBUG, INFO, WARN, ERROR, CRITICAL
    };
    // -- Fields: -----------------------------------------------------------
    /** Label of this log level, used for display. **/
    private final String label;
    /** Numeric severity of this log level. Higher means more severe. **/
    private final int severity;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a log level with a display label and numeric severity.
    *
    * @param label the display label
    * @param severity the numeric severity value
    **/
    private LogLevel(String label, int severity){
        this.label = label;
        this.severity = severity;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the display label.
    *
    * @return the label string (for example, {@code "INFO"}, {@code "WARN"}), never {@code null}
    **/
    public String getLabel(){
        return label;
    }
    /**
    * Returns the same value as {@link #getLabel()}.
    *
    * @return the label string (for example, {@code "INFO"}, {@code "WARN"}), never {@code null}
    **/
    @Override
    public String toString(){
        return label;
    }
    /**
    * Returns the numeric severity of this level. A higher value means a more severe level.
    *
    * @return the numeric severity value (for example, {@code 2} for INFO, {@code 3} for WARN)
    **/
    public int getSeverity(){
        return severity;
    }
    // -- Utility Methods: --------------------------------------------------
    /**
    * Returns a copy of all supported log levels.
    *
    * @return a copy of all supported log levels, never {@code null}
    **/
    public static LogLevel[] getValues(){
        return VALUES.clone();
    }
    /**
    * Finds a log level by its display label.
    *
    * <p>
    *   The lookup is case-insensitive and trims leading and trailing spaces.
    *   If no match is found, an internal diagnostic warning is emitted and the default level is returned.
    * </p>
    *
    * @param label the level label to search for, or {@code null} or blank to return the default level
    * @return the matching log level, or the default level if not found
    **/
    public static LogLevel fromLabel(String label){
        if(label == null || label.isBlank()){
            InternalDiagnostic.warn("LogLevel.fromLabel: label is null/blank -> returning DEFAULT_LEVEL=" + DEFAULT_LEVEL.label);
            return DEFAULT_LEVEL;
        }
        label = label.trim();
        for(LogLevel level : VALUES)
            if(level.label.equalsIgnoreCase(label))
                return level;
        InternalDiagnostic.warn("LogLevel.fromLabel: no level found named \"" + label + "\" -> returning DEFAULT_LEVEL=" + DEFAULT_LEVEL.label);
        return DEFAULT_LEVEL;
    }
    /**
    * Finds a log level by its numeric severity.
    *
    * <p>
    *   Valid severities match those of the known levels. Implemented as a direct
    *   {@link #VALUES} array index — see the invariant documented on {@link #VALUES}.
    *   If no match is found, an internal diagnostic warning is emitted and the default level is returned.
    * </p>
    *
    * @param severity the severity value to search for
    * @return the matching log level, or the default level if not found
    **/
    public static LogLevel fromSeverity(int severity){
        if(severity >= 0 && severity < VALUES.length)
            return VALUES[severity];
        InternalDiagnostic.warn(
            "LogLevel.fromSeverity: no level found for severity (" + severity + ") -> returning DEFAULT_LEVEL=" + DEFAULT_LEVEL.label
        );
        return DEFAULT_LEVEL;
    }
    /**
    * Checks whether this level is equal to or higher than the given threshold.
    *
    * <p>
    *   This is used to determine if a log entry meets the minimum level required by a {@link Logger}.
    *   If the threshold is {@code null}, an internal diagnostic warning is emitted and the default level is used as the threshold.
    * </p>
    *
    * @param threshold the minimum required level, or {@code null} to use the default level
    * @return {@code true} if this level meets or exceeds the threshold, otherwise {@code false}
    **/
    public boolean isAtLeast(LogLevel threshold){
        if(threshold == null){
            InternalDiagnostic.warn("LogLevel.isAtLeast: threshold is null -> using DEFAULT_LEVEL=" + DEFAULT_LEVEL.label);
            threshold = DEFAULT_LEVEL;
        }
        return this.severity >= threshold.severity;
    }
    /**
    * Checks whether this level is equal to or lower than the given threshold.
    *
    * <p>
    *   This can be used for filtering log entries that are too verbose for a given context.
    *   If the threshold is {@code null}, an internal diagnostic warning is emitted and the default level is used as the threshold.
    * </p>
    *
    * @param threshold the maximum allowed level, or {@code null} to use the default level
    * @return {@code true} if this level does not exceed the threshold, otherwise {@code false}
    **/
    public boolean isAtMost(LogLevel threshold){
        if(threshold == null){
            InternalDiagnostic.warn("LogLevel.isAtMost: threshold is null -> using DEFAULT_LEVEL=" + DEFAULT_LEVEL.label);
            threshold = DEFAULT_LEVEL;
        }
        return this.severity <= threshold.severity;
    }
}