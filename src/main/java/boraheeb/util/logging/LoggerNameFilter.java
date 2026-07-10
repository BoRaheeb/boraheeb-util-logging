package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.LinkedHashSet;
import java.util.Set;
// -- LoggerNameFilter Class: -------------------------------------------
/**
* Built-in {@link LogFilter} implementations that filter log records by
* logger name.
*
* <p>
*   All methods return a {@link LogFilter} that can be passed to any
*   {@link LogOutput} builder. The returned filters can be further composed
*   using {@link LogFilter#and(LogFilter)}, {@link LogFilter#or(LogFilter)},
*   and {@link LogFilter#negate()}.
* </p>
*
* <pre>{@code
*   // only records from the "db" or "auth" loggers
*   LogOutput output = FileLogOutput.builder()
*       .filter(LoggerNameFilter.include("db", "auth"))
*       .build();
*
*   // all records except from "noisy.lib"
*   LogOutput output = ConsoleLogOutput.builder()
*       .filter(LoggerNameFilter.exclude("noisy.lib"))
*       .build();
*
*   // all records from the "boraheeb.ui" hierarchy
*   LogFilter uiFilter = LoggerNameFilter.prefix("boraheeb.ui");
*
*   // combined with LevelFilter
*   LogFilter f = LoggerNameFilter.include("db").and(LevelFilter.atLeast(LogLevel.WARN));
* }</pre>
*
* <p>
*   {@link #include} and {@link #exclude} use exact matching with O(1) lookup.
*   {@link #prefix} uses prefix matching with O(k) lookup where k is the number
*   of prefixes.
* </p>
*
* <p>This class is a static utility class and is thread-safe.</p>
*
* @author BoRaheeb
* @see LogFilter
* @see LevelFilter
**/
public final class LoggerNameFilter{
    // -- Constructors: -----------------------------------------------------
    /** Private constructor — This class is a static utility class and cannot be created. **/
    private LoggerNameFilter(){}
    // -- Factory Methods: --------------------------------------------------
    /**
    * Returns a filter that accepts only records whose logger name exactly
    * matches one of the given names.
    *
    * <p>
    *   Null or blank entries in {@code names} are skipped with an internal diagnostic warning.
    *   If no valid names remain after filtering, an internal diagnostic warning is emitted and
    *   {@link LogFilter#REJECT_ALL} is returned.
    * </p>
    *
    * @param names the logger names to accept, or {@code null} or empty to reject all loggers
    * @return a filter accepting only records from the given loggers, never {@code null}
    **/
    public static LogFilter include(String... names){
        if(names == null || names.length == 0){
            InternalDiagnostic.warn("LoggerNameFilter.include: names is null/empty -> returning REJECT_ALL");
            return LogFilter.REJECT_ALL;
        }
        Set<String> nameSet = new LinkedHashSet<>();
        for(String name : names){
            if(name == null || name.isBlank())
                InternalDiagnostic.warn("LoggerNameFilter.include: name is null/blank -> entry ignored");
            else nameSet.add(name.trim());
        }
        if(nameSet.isEmpty()){
            InternalDiagnostic.warn("LoggerNameFilter.include: all names were null/blank -> returning REJECT_ALL");
            return LogFilter.REJECT_ALL;
        }
        return record -> (nameSet.contains(record.getLoggerName()));
    }
    /**
    * Returns a filter that accepts only records whose logger name starts with
    * one of the given prefixes.
    *
    * <p>
    *   Use this to match an entire logger hierarchy — for example,
    *   {@code prefix("boraheeb.ui")} accepts {@code "boraheeb.ui.dialog"},
    *   {@code "boraheeb.ui.MainWindow"}, and so on.
    * </p>
    *
    * <p>
    *   Matching is case-sensitive — {@code "boraheeb.ui"} does not match
    *   {@code "Boraheeb.UI"}. Null or blank entries in {@code prefixes} are
    *   skipped with an internal diagnostic warning. If no valid prefixes remain after filtering,
    *   an internal diagnostic warning is emitted and {@link LogFilter#REJECT_ALL} is returned.
    * </p>
    *
    * @param prefixes the logger name prefixes to accept, or {@code null} or empty to reject all loggers
    * @return a filter accepting records from loggers matching any prefix, never {@code null}
    **/
    public static LogFilter prefix(String... prefixes){
        if(prefixes == null || prefixes.length == 0){
            InternalDiagnostic.warn("LoggerNameFilter.prefix: prefixes is null/empty -> returning REJECT_ALL");
            return LogFilter.REJECT_ALL;
        }
        Set<String> prefixSet = new LinkedHashSet<>();
        for(String prefix : prefixes){
            if(prefix == null || prefix.isBlank())
                InternalDiagnostic.warn("LoggerNameFilter.prefix: prefix is null/blank -> entry ignored");
            else prefixSet.add(prefix.trim());
        }
        if(prefixSet.isEmpty()){
            InternalDiagnostic.warn("LoggerNameFilter.prefix: all prefixes were null/blank -> returning REJECT_ALL");
            return LogFilter.REJECT_ALL;
        }
        return record -> {
            String name = record.getLoggerName();
            for(String prefix : prefixSet)
                if(name.startsWith(prefix)) return true;
            return false;
        };
    }
    /**
    * Returns a filter that accepts all records except those whose logger name
    * exactly matches one of the given names.
    *
    * <p>
    *   Null or blank entries in {@code names} are skipped with an internal diagnostic warning.
    *   If no valid names remain after filtering, an internal diagnostic warning is emitted and
    *   {@link LogFilter#ACCEPT_ALL} is returned.
    * </p>
    *
    * @param names the logger names to reject, or {@code null} or empty to accept all loggers
    * @return a filter rejecting records from the given loggers, never {@code null}
    **/
    public static LogFilter exclude(String... names){
        if(names == null || names.length == 0){
            InternalDiagnostic.warn("LoggerNameFilter.exclude: names is null/empty -> returning ACCEPT_ALL");
            return LogFilter.ACCEPT_ALL;
        }
        Set<String> nameSet = new LinkedHashSet<>();
        for(String name : names){
            if(name == null || name.isBlank())
                InternalDiagnostic.warn("LoggerNameFilter.exclude: name is null/blank -> entry ignored");
            else nameSet.add(name.trim());
        }
        if(nameSet.isEmpty()){
            InternalDiagnostic.warn("LoggerNameFilter.exclude: all names were null/blank -> returning ACCEPT_ALL");
            return LogFilter.ACCEPT_ALL;
        }
        return record -> (!nameSet.contains(record.getLoggerName()));
    }
}