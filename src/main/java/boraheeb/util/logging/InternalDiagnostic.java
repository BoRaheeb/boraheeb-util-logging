package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// -- InternalDiagnostic Class: -----------------------------------------
/**
* Internal diagnostic utility for the logging system.
*
* <p>
*   Reports invalid arguments, null values, and unexpected states
*   without throwing exceptions, so the logging system keeps running
*   even when misconfigured.
* </p>
*
* <p>
*   All output goes to {@code System.err} with a fixed prefix
*   so developers can quickly identify and fix misuse.
* </p>
*
* <p>
*   To avoid console spam, repeated diagnostic messages are temporarily hidden.
*   The first occurrence is printed immediately.
*   Repeated occurrences are counted and reported later.
* </p>
*
* <p>
*   If a message stops repeating while it is still hidden, its hidden count
*   is lost. The count is never printed by itself — it only appears if that
*   same message happens again later, attached to its next print.
* </p>
*
* <p>
*   Suppression state is bounded: if more than {@code MAX_TRACKED_KEYS} distinct
*   diagnostics are being tracked at once, all suppression state is cleared in a
*   single pass rather than evicted individually (for example, least-recently-used).
*   This bounds memory use at the cost of an occasional burst of reprints for
*   diagnostics that were mid-cooldown when the clear happened.
* </p>
*
* <p><b>This class is package-private and is not part of the public API.</b></p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
final class InternalDiagnostic{
    // -- Constants: --------------------------------------------------------
    /** Prefix added to every diagnostic message. **/
    private static final String PREFIX = "> [boraheeb.util.logging]";
    /** Time period during which repeated diagnostics are temporarily suppressed and counted. **/
    private static final long DIAGNOSTIC_COOLDOWN_NANOS = 1_000_000_000L;
    /** Maximum number of distinct diagnostics tracked for suppression before the state is cleared. **/
    private static final int MAX_TRACKED_KEYS = 512;
    /** Index into the per-diagnostic state array holding the last-printed monotonic timestamp. **/
    private static final int STATE_LAST_PRINTED_NANOS = 0;
    /** Index into the per-diagnostic state array holding the suppressed-occurrence count. **/
    private static final int STATE_SUPPRESSED_COUNT = 1;
    /** Stores suppression state for repeated diagnostics. **/
    private static final Map<String, long[]> SUPPRESSION_STATE = new ConcurrentHashMap<>();
    // -- Constructors: -----------------------------------------------------
    /** Private constructor — This class is a static utility class and cannot be created. **/
    private InternalDiagnostic(){}
    // -- Diagnostic Methods: -----------------------------------------------
    /**
    * Prints an informational diagnostic message to {@code System.err}.
    *
    * @param message the message to print
    **/
    static void info(String message){
        print("INFO", message);
    }
    /**
    * Prints a warning diagnostic message to {@code System.err}.
    *
    * @param message the message to print
    **/
    static void warn(String message){
        print("WARN", message);
    }
    /**
    * Prints an error diagnostic message to {@code System.err}.
    *
    * @param message the message to print
    **/
    static void error(String message){
        print("ERROR", message);
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Prints a diagnostic message with a level.
    *
    * <p>
    *   To avoid console spam, repeated calls with the same level and message
    *   are hidden until the cooldown period passes. The first call for a given
    *   level and message always prints right away. Once the cooldown passes,
    *   the next matching call prints again and includes how many repeats were
    *   hidden since the last print.
    * </p>
    *
    * @param level the diagnostic level
    * @param message the message to print
    **/
    private static void print(String level, String message){
        String key = level + ":" + message;
        String[] toPrint = new String[1];
        long now = System.nanoTime();
        if(SUPPRESSION_STATE.size() > MAX_TRACKED_KEYS) SUPPRESSION_STATE.clear();
        SUPPRESSION_STATE.compute(key, (k, state) -> {
            if(state == null || (now - state[STATE_LAST_PRINTED_NANOS]) >= DIAGNOSTIC_COOLDOWN_NANOS){
                long suppressed = ((state == null)? 0 : state[STATE_SUPPRESSED_COUNT]);
                toPrint[0] = (suppressed > 0)?
                    message + " [suppressed " + suppressed + "x in the last " + (DIAGNOSTIC_COOLDOWN_NANOS / 1_000_000_000L) + "s]" : message;
                return new long[]{now, 0};
            }
            state[STATE_SUPPRESSED_COUNT]++;
            return state;
        });
        if(toPrint[0] != null)
            System.err.println(PREFIX + "[" + level + "] " + toPrint[0]);
    }
}