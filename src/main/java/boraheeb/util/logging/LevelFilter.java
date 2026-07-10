package boraheeb.util.logging;
// -- LevelFilter Class: ------------------------------------------------
/**
* Built-in {@link LogFilter} implementations that filter log records by
* severity level.
*
* <p>
*   All methods return a {@link LogFilter} that can be passed to any
*   {@link LogOutput} builder. The returned filters can be further composed
*   using {@link LogFilter#and(LogFilter)}, {@link LogFilter#or(LogFilter)},
*   and {@link LogFilter#negate()}.
* </p>
*
* <pre>{@code
*   // WARN and above only
*   LogOutput output = ConsoleLogOutput.builder()
*       .filter(LevelFilter.atLeast(LogLevel.WARN))
*       .build();
*
*   // DEBUG through WARN only
*   LogOutput output = FileLogOutput.builder()
*       .filter(LevelFilter.between(LogLevel.DEBUG, LogLevel.WARN))
*       .build();
*
*   // combined with LoggerNameFilter
*   LogFilter f = LevelFilter.atLeast(LogLevel.ERROR).and(LoggerNameFilter.include("db", "auth"));
* }</pre>
*
* <p>This class is a static utility class and is thread-safe.</p>
*
* @author BoRaheeb
* @see LogFilter
* @see LoggerNameFilter
**/
public final class LevelFilter{
    // -- Constructors: -----------------------------------------------------
    /** Private constructor — This class is a static utility class and cannot be created. **/
    private LevelFilter(){}
    // -- Factory Methods: --------------------------------------------------
    /**
    * Returns a filter that accepts records whose level is equal to or higher
    * than the given level.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default level is used.</p>
    *
    * @param level the minimum required level, or {@code null} to use the default level
    * @return a filter accepting records at or above the given level, never {@code null}
    **/
    public static LogFilter atLeast(LogLevel level){
        if(level == null){
            InternalDiagnostic.warn("LevelFilter.atLeast: level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            level = LogLevel.DEFAULT_LEVEL;
        }
        final LogLevel threshold = level;
        return record -> (record.getLevel().isAtLeast(threshold));
    }
    /**
    * Returns a filter that accepts records whose level is equal to or lower
    * than the given level.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default level is used.</p>
    *
    * @param level the maximum allowed level, or {@code null} to use the default level
    * @return a filter accepting records at or below the given level, never {@code null}
    **/
    public static LogFilter atMost(LogLevel level){
        if(level == null){
            InternalDiagnostic.warn("LevelFilter.atMost: level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            level = LogLevel.DEFAULT_LEVEL;
        }
        final LogLevel threshold = level;
        return record -> (record.getLevel().isAtMost(threshold));
    }
    /**
    * Returns a filter that accepts records whose level matches exactly
    * the given level.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default level is used.</p>
    *
    * @param level the exact level to match, or {@code null} to use the default level
    * @return a filter accepting only records at the given level, never {@code null}
    **/
    public static LogFilter exactly(LogLevel level){
        if(level == null){
            InternalDiagnostic.warn("LevelFilter.exactly: level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            level = LogLevel.DEFAULT_LEVEL;
        }
        final LogLevel target = level;
        return record -> (record.getLevel() == target);
    }
    /**
    * Returns a filter that accepts records whose level falls between
    * {@code min} and {@code max} inclusive.
    *
    * <p>If either {@code min} or {@code max} is {@code null}, an internal diagnostic warning is emitted and the default level is used.</p>
    *
    * <p>
    *   If {@code min} has a higher severity than {@code max}, an internal diagnostic warning is
    *   emitted and the two are swapped automatically before the filter is created.
    * </p>
    *
    * @param min the minimum required level (inclusive), or {@code null} to use the default level
    * @param max the maximum allowed level (inclusive), or {@code null} to use the default level
    * @return a filter accepting records within the given range, never {@code null}
    **/
    public static LogFilter between(LogLevel min, LogLevel max){
        if(min == null){
            InternalDiagnostic.warn("LevelFilter.between: min is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            min = LogLevel.DEFAULT_LEVEL;
        }
        if(max == null){
            InternalDiagnostic.warn("LevelFilter.between: max is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            max = LogLevel.DEFAULT_LEVEL;
        }
        if(min.getSeverity() > max.getSeverity()){
            InternalDiagnostic.warn(
                "LevelFilter.between: min (" + min.getLabel() + "(" + min.getSeverity() + ")) " +
                "is more severe than max (" + max.getLabel() + "(" + max.getSeverity() + ")) -> swapping"
            );
            LogLevel temp = min;
            min = max;
            max = temp;
        }
        final LogLevel low = min;
        final LogLevel high = max;
        return record -> (record.getLevel().isAtLeast(low) && record.getLevel().isAtMost(high));
    }
}