package boraheeb.util.logging;
// -- LogFormatter Interface: -------------------------------------------
/**
* Contract for formatting a {@link LogRecord} into a string.
*
* <p>
*   Implementations decide how a record is rendered — as human-readable
*   text, JSON, XML, or any other format. Each {@link LogOutput} holds one
*   {@code LogFormatter} and uses it in every call to
*   {@link LogOutput#publish(LogRecord)}.
* </p>
*
* <p>Two built-in implementations are provided:</p>
* <ul>
*   <li>
*       {@link TextLogFormatter} — styled, human-readable text with optional
*       ANSI colors via {@link LogTheme}. Use for console and file output.
*   </li>
*   <li>
*       {@link JsonLogFormatter} — single-line JSON object per record.
*       Use for log aggregators, structured pipelines, and machine consumers.
*   </li>
* </ul>
*
* <p>
*   Because {@code LogFormatter} is a {@link FunctionalInterface}, custom
*   formatters can be expressed as lambdas:
* </p>
* <pre>{@code
*   // simple custom formatter
*   LogFormatter compact = record -> record.getLevel().getLabel() + ": " + record.getMessage();
* }</pre>
*
* <p>
*   Implementations must be thread-safe, as a single formatter instance
*   is typically shared across multiple threads via a {@link LogOutput}.
* </p>
*
* @author BoRaheeb
* @see TextLogFormatter
* @see JsonLogFormatter
**/
@FunctionalInterface
public interface LogFormatter{
    // -- Interface Methods: ------------------------------------------------
    /**
    * Formats the given log record into a string.
    *
    * <p>
    *   Implementations should handle a {@code null} record gracefully — for
    *   example, by returning an empty string, or by reporting the problem
    *   through whatever diagnostic mechanism the implementation uses.
    * </p>
    *
    * @param record the log record to format
    * @return the formatted string, never {@code null}
    **/
    String format(LogRecord record);
    // -- Default Methods: --------------------------------------------------
    /**
    * Returns the persistent terminal background ANSI escape code for this
    * formatter, or {@code null} if no background is configured.
    *
    * <p>
    *   When non-null, callers may write this code to the terminal once before
    *   publishing records to establish a persistent background color that
    *   remains active across log entries.
    * </p>
    *
    * <p>
    *   The default implementation returns {@code null}. Override this method
    *   when the formatter is tied to a {@link LogTheme} that configures a
    *   terminal background.
    * </p>
    *
    * @return the terminal background escape code, or {@code null}
    * @see LogTheme#getTerminalBackground()
    **/
    default String terminalBackground(){
        return null;
    }
}