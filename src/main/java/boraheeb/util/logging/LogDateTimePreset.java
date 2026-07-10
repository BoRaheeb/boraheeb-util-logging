package boraheeb.util.logging;
// -- LogDateTimePreset Class: ------------------------------------------
/**
* Represents a named date-time formatting preset for logging.
*
* <p>Each preset contains:</p>
* <ul>
*   <li>A stable preset label (for example, {@code "LOG_READABLE"})</li>
*   <li>A date-time pattern string (for example, {@code "yyyy-MM-dd HH:mm:ss"})</li>
* </ul>
*
* <p>
*   These presets provide ready-to-use formatting options for console output,
*   file-safe names, ISO formats, and human-friendly display styles.
* </p>
*
* <p>This class is immutable and thread-safe.</p>
*
* @author BoRaheeb
**/
public final class LogDateTimePreset{
    // -- Constants: --------------------------------------------------------
        // -- Date Only: --------------------------------------------------------
            /** Short numeric date format (for example, {@code "2026/01/13"}). **/
            public static final LogDateTimePreset DATE_SHORT = new LogDateTimePreset("DATE_SHORT", "yyyy/MM/dd");
            /** Medium readable date format (for example, {@code "Jan 13, 2026"}). **/
            public static final LogDateTimePreset DATE_MEDIUM = new LogDateTimePreset("DATE_MEDIUM", "MMM d, yyyy");
            /** Long readable date format (for example, {@code "January 13, 2026"}). **/
            public static final LogDateTimePreset DATE_LONG = new LogDateTimePreset("DATE_LONG", "MMMM d, yyyy");
            /** ISO date-only format (for example, {@code "2026-01-13"}). **/
            public static final LogDateTimePreset DATE_ISO = new LogDateTimePreset("DATE_ISO", "yyyy-MM-dd");
        // -- Time Only: --------------------------------------------------------
            /** 12-hour time format (for example, {@code "08:45 PM"}). **/
            public static final LogDateTimePreset TIME_12H = new LogDateTimePreset("TIME_12H", "hh:mm a");
            /** 12-hour time format with seconds (for example, {@code "08:45:12 PM"}). **/
            public static final LogDateTimePreset TIME_12H_FULL = new LogDateTimePreset("TIME_12H_FULL", "hh:mm:ss a");
            /** 24-hour time format (for example, {@code "20:45"}). **/
            public static final LogDateTimePreset TIME_24H = new LogDateTimePreset("TIME_24H", "HH:mm");
            /** 24-hour time format with seconds (for example, {@code "20:45:12"}). **/
            public static final LogDateTimePreset TIME_24H_FULL = new LogDateTimePreset("TIME_24H_FULL", "HH:mm:ss");
            /** 24-hour time format with seconds and milliseconds (for example, {@code "20:45:12.123"}). **/
            public static final LogDateTimePreset TIME_24H_WITH_MILLIS = new LogDateTimePreset("TIME_24H_WITH_MILLIS", "HH:mm:ss.SSS");
        // -- Date & Time: ------------------------------------------------------
            /** Short date-time format in 12-hour style (for example, {@code "2026/01/13 08:45 PM"}). **/
            public static final LogDateTimePreset DATETIME_SHORT = new LogDateTimePreset("DATETIME_SHORT", "yyyy/MM/dd hh:mm a");
            /** Medium date-time format in 12-hour style with seconds (for example, {@code "Jan 13, 2026 08:45:12 PM"}). **/
            public static final LogDateTimePreset DATETIME_MEDIUM = new LogDateTimePreset("DATETIME_MEDIUM", "MMM d, yyyy hh:mm:ss a");
            /** Short date-time format in 24-hour style (for example, {@code "2026/01/13 20:45"}). **/
            public static final LogDateTimePreset DATETIME_24H = new LogDateTimePreset("DATETIME_24H", "yyyy/MM/dd HH:mm");
            /** Full date-time format in 24-hour style with seconds (for example, {@code "2026/01/13 20:45:12"}). **/
            public static final LogDateTimePreset DATETIME_24H_FULL = new LogDateTimePreset("DATETIME_24H_FULL", "yyyy/MM/dd HH:mm:ss");
        // -- ISO 8601: ---------------------------------------------------------
            /** ISO local date-time format without milliseconds (for example, {@code "2026-01-13T20:45:12"}). **/
            public static final LogDateTimePreset ISO_LOCAL = new LogDateTimePreset("ISO_LOCAL", "yyyy-MM-dd'T'HH:mm:ss");
            /** ISO local date-time format with milliseconds (for example, {@code "2026-01-13T20:45:12.123"}). **/
            public static final LogDateTimePreset ISO_WITH_MILLIS = new LogDateTimePreset("ISO_WITH_MILLIS", "yyyy-MM-dd'T'HH:mm:ss.SSS");
        // -- Distributed / Cross-Zone Systems: ---------------------------------
            /** ISO date-time with milliseconds and UTC marker/offset (for example, {@code "2026-01-13T17:45:12.123Z"}). **/
            public static final LogDateTimePreset SYSTEM_ISO_UTC = new LogDateTimePreset("SYSTEM_ISO_UTC", "yyyy-MM-dd'T'HH:mm:ss.SSSX");
            /** ISO date-time with milliseconds and numeric offset (for example, {@code "2026-01-13T20:45:12.123+03:00"}). **/
            public static final LogDateTimePreset SYSTEM_ISO_OFFSET = new LogDateTimePreset("SYSTEM_ISO_OFFSET", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            /** ISO date-time with milliseconds and explicit zone ID (for example, {@code "2026-01-13T20:45:12.123 Asia/Bahrain"}). **/
            public static final LogDateTimePreset SYSTEM_WITH_ZONE_ID = new LogDateTimePreset("SYSTEM_WITH_ZONE_ID", "yyyy-MM-dd'T'HH:mm:ss.SSS VV");
        // -- Logging Timestamps: -----------------------------------------------
            /** Readable log timestamp (for example, {@code "2026-01-13 20:45:12"}). **/
            public static final LogDateTimePreset LOG_READABLE = new LogDateTimePreset("LOG_READABLE", "yyyy-MM-dd HH:mm:ss");
            /** Readable log timestamp with milliseconds (for example, {@code "2026-01-13 20:45:12.123"}). **/
            public static final LogDateTimePreset LOG_WITH_MILLIS = new LogDateTimePreset("LOG_WITH_MILLIS", "yyyy-MM-dd HH:mm:ss.SSS");
            /** Readable log timestamp with nanoseconds (for example, {@code "2026-01-13 20:45:12.123456"}). **/
            public static final LogDateTimePreset LOG_WITH_NANOS = new LogDateTimePreset("LOG_WITH_NANOS", "yyyy-MM-dd HH:mm:ss.SSSSSS");
        // -- Human Friendly: ---------------------------------------------------
            /** Human-friendly short date (for example, {@code "Tue, Jan 13"}). **/
            public static final LogDateTimePreset FRIENDLY_SHORT = new LogDateTimePreset("FRIENDLY_SHORT", "EEE, MMM d");
            /** Human-friendly medium date (for example, {@code "Tue, Jan 13, 2026"}). **/
            public static final LogDateTimePreset FRIENDLY_MEDIUM = new LogDateTimePreset("FRIENDLY_MEDIUM", "EEE, MMM d, yyyy");
            /** Human-friendly long date-time format (for example, {@code "Tuesday, January 13, 2026 8:45 PM"}). **/
            public static final LogDateTimePreset FRIENDLY_LONG = new LogDateTimePreset("FRIENDLY_LONG", "EEEE, MMMM d, yyyy h:mm a");
        // -- File / Path Safe: -------------------------------------------------
            /** File-safe date format with no separators (for example, {@code "20260113"}). **/
            public static final LogDateTimePreset FILENAME_DATE = new LogDateTimePreset("FILENAME_DATE", "yyyyMMdd");
            /** File-safe date-time format (for example, {@code "20260113_204512"}). **/
            public static final LogDateTimePreset FILENAME_DATETIME = new LogDateTimePreset("FILENAME_DATETIME", "yyyyMMdd_HHmmss");
            /** File-safe full date-time format with milliseconds (for example, {@code "20260113_204512_123"}). **/
            public static final LogDateTimePreset FILENAME_FULL = new LogDateTimePreset("FILENAME_FULL", "yyyyMMdd_HHmmss_SSS");
    /** Default preset, used as a fallback for null or invalid input. **/
    public static final LogDateTimePreset DEFAULT_PRESET = LOG_READABLE;
    /** All supported date-time presets. **/
    private static final LogDateTimePreset[] VALUES = {
        DATE_SHORT, DATE_MEDIUM, DATE_LONG, DATE_ISO,
        TIME_12H, TIME_12H_FULL, TIME_24H, TIME_24H_FULL, TIME_24H_WITH_MILLIS,
        DATETIME_SHORT, DATETIME_MEDIUM, DATETIME_24H, DATETIME_24H_FULL,
        ISO_LOCAL, ISO_WITH_MILLIS,
        SYSTEM_ISO_UTC, SYSTEM_ISO_OFFSET, SYSTEM_WITH_ZONE_ID,
        LOG_READABLE, LOG_WITH_MILLIS, LOG_WITH_NANOS,
        FRIENDLY_SHORT, FRIENDLY_MEDIUM, FRIENDLY_LONG,
        FILENAME_DATE, FILENAME_DATETIME, FILENAME_FULL
    };
    // -- Fields: -----------------------------------------------------------
    /** Preset label used for lookup and display. **/
    private final String label;
    /** Date-time pattern used by this preset. **/
    private final String pattern;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a date-time preset with a label and formatting pattern.
    *
    * @param label the preset label
    * @param pattern the date-time pattern
    **/
    private LogDateTimePreset(String label, String pattern){
        this.label = label;
        this.pattern = pattern;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the preset label.
    *
    * @return the label string (for example, {@code "LOG_READABLE"}, {@code "DATE_MEDIUM"}), never {@code null}
    **/
    public String getLabel(){
        return label;
    }
    /**
    * Returns the same value as {@link #getLabel()}.
    *
    * @return the label string (for example, {@code "LOG_READABLE"}, {@code "DATE_MEDIUM"}), never {@code null}
    **/
    @Override
    public String toString(){
        return label;
    }
    /**
    * Returns the date-time pattern of this preset.
    *
    * @return the date-time pattern (for example, {@code "yyyy-MM-dd HH:mm:ss"}, {@code "MMM d, yyyy"}), never {@code null}
    **/
    public String getPattern(){
        return pattern;
    }
    // -- Utility Methods: --------------------------------------------------
    /**
    * Returns a copy of all supported date-time presets.
    *
    * @return a copy of all supported date-time presets, never {@code null}
    **/
    public static LogDateTimePreset[] getValues(){
        return VALUES.clone();
    }
    /**
    * Finds a date-time preset by its label.
    *
    * <p>
    *   The lookup is case-insensitive and trims leading and trailing spaces.
    *   If no match is found, an internal diagnostic warning is emitted and
    *   the default preset is returned.
    * </p>
    *
    * @param label the preset label to search for, or {@code null} or blank to return the default preset
    * @return the matching preset, or the default preset if not found
    **/
    public static LogDateTimePreset fromLabel(String label){
        if(label == null || label.isBlank()){
            InternalDiagnostic.warn("LogDateTimePreset.fromLabel: label is null/blank -> returning DEFAULT_PRESET=" + DEFAULT_PRESET.label);
            return DEFAULT_PRESET;
        }
        label = label.trim();
        for(LogDateTimePreset preset : VALUES)
            if(preset.label.equalsIgnoreCase(label))
                return preset;
        InternalDiagnostic.warn(
            "LogDateTimePreset.fromLabel: no preset found named \"" + label + "\" -> returning DEFAULT_PRESET=" + DEFAULT_PRESET.label
        );
        return DEFAULT_PRESET;
    }
    /**
    * Finds a date-time preset by its exact pattern.
    *
    * <p>
    *   The lookup trims leading and trailing spaces but is otherwise
    *   exact and case-sensitive, since date-time pattern letters are
    *   case-sensitive by definition (for example, {@code "HH"} means
    *   24-hour clock while {@code "hh"} means 12-hour clock).
    *   If no match is found, an internal diagnostic warning is emitted
    *   and the default preset is returned.
    * </p>
    *
    * @param pattern the date-time pattern to search for, or {@code null} or blank to return the default preset
    * @return the matching preset, or the default preset if not found
    **/
    public static LogDateTimePreset fromPattern(String pattern){
        if(pattern == null || pattern.isBlank()){
            InternalDiagnostic.warn("LogDateTimePreset.fromPattern: pattern is null/blank -> returning DEFAULT_PRESET=" + DEFAULT_PRESET.label);
            return DEFAULT_PRESET;
        }
        pattern = pattern.trim();
        for(LogDateTimePreset preset : VALUES)
            if(preset.pattern.equals(pattern))
                return preset;
        InternalDiagnostic.warn(
            "LogDateTimePreset.fromPattern: no preset found for pattern \"" + pattern + "\" -> returning DEFAULT_PRESET=" + DEFAULT_PRESET.label
        );
        return DEFAULT_PRESET;
    }
}