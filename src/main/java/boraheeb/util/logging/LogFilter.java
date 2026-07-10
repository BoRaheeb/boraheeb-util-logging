package boraheeb.util.logging;
// -- LogFilter Interface: ----------------------------------------------
/**
* A predicate that decides whether a {@link LogRecord} should be published
* by a {@link LogOutput}.
*
* <p>
*   Each {@link LogOutput} can hold an optional {@code LogFilter}. When set,
*   the filter is evaluated in {@link LogOutput#publish(LogRecord)} after the
*   minimum-level check and before any formatting or I/O. Records for which
*   {@link #accept(LogRecord)} returns {@code false} are silently dropped.
* </p>
*
* <p>
*   Because {@code LogFilter} is a {@link FunctionalInterface}, filters can
*   be expressed as lambdas or method references:
* </p>
* <pre>{@code
*   // only records from a specific logger
*   LogFilter onlyUi = r -> "boraheeb.ui".equals(r.getLoggerName());
*
*   // only records with a specific field
*   LogFilter onlyRequests = r -> r.getFields().containsKey("requestId");
*
*   // only ERROR and above
*   LogFilter errorsOnly = r -> r.getLevel().isAtLeast(LogLevel.ERROR);
*
*   // compose with and()
*   LogFilter combined = onlyUi.and(errorsOnly);
*
*   // negate
*   LogFilter notUi = onlyUi.negate();
* }</pre>
*
* <p>
*   Filters can be composed using {@link #and(LogFilter)}, {@link #or(LogFilter)},
*   and {@link #negate()} without writing raw lambdas.
* </p>
*
* <p>
*   Implementations must be thread-safe, as the same filter instance is
*   called from whichever thread publishes to the output.
* </p>
*
* @author BoRaheeb
* @see LevelFilter
* @see LoggerNameFilter
**/
@FunctionalInterface
public interface LogFilter{
    // -- Constants: --------------------------------------------------------
    /**
    * A filter that unconditionally accepts every log record.
    *
    * <p>
    *   Use this as an explicit "no filter" value instead of {@code null}
    *   when a filter field is required but no filtering is desired.
    * </p>
    **/
    LogFilter ACCEPT_ALL = record -> true;
    /**
    * A filter that unconditionally rejects every log record.
    *
    * <p>
    *   Attaching this filter to a {@link LogOutput} effectively silences
    *   that output without removing it from the logger configuration.
    *   Unlike disabling the logger, other outputs on the same logger are
    *   unaffected and continue to receive records normally.
    * </p>
    **/
    LogFilter REJECT_ALL = record -> false;
    // -- Interface Methods: ------------------------------------------------
    /**
    * Returns {@code true} if the given record should be published,
    * or {@code false} if it should be silently dropped.
    *
    * @param record the log record to evaluate; never {@code null}
    * @return {@code true} to publish, {@code false} to drop
    **/
    boolean accept(LogRecord record);
    // -- Default Methods: --------------------------------------------------
    /**
    * Returns a composed filter that accepts a record only if both this filter
    * and {@code other} accept it.
    *
    * <p>If {@code other} is {@code null}, an internal diagnostic warning is emitted and this filter is returned unchanged.</p>
    *
    * @param other the filter to combine with, or {@code null} to return this filter
    * @return a composed filter representing {@code this AND other}, never {@code null}
    **/
    default LogFilter and(LogFilter other){
        if(other == null){
            InternalDiagnostic.warn("LogFilter.and: other is null -> returning this filter unchanged");
            return this;
        }
        return record -> (this.accept(record) && other.accept(record));
    }
    /**
    * Returns a composed filter that accepts a record if either this filter
    * or {@code other} accepts it.
    *
    * <p>If {@code other} is {@code null}, an internal diagnostic warning is emitted and this filter is returned unchanged.</p>
    *
    * @param other the filter to combine with, or {@code null} to return this filter
    * @return a composed filter representing {@code this OR other}, never {@code null}
    **/
    default LogFilter or(LogFilter other){
        if(other == null){
            InternalDiagnostic.warn("LogFilter.or: other is null -> returning this filter unchanged");
            return this;
        }
        return record -> (this.accept(record) || other.accept(record));
    }
    /**
    * Returns a filter that is the logical negation of this filter.
    *
    * @return the logical negation of this filter, never {@code null}
    **/
    default LogFilter negate(){
        return record -> (!this.accept(record));
    }
}