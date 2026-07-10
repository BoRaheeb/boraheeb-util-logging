package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
// -- MemoryLogOutput Class: --------------------------------------------
/**
* A {@link LogOutput} that retains the most recent log records in a bounded,
* in-memory ring buffer, overwriting the oldest record once it is full.
*
* <p>
*   Unlike {@link AsyncLogOutput}, which drops <em>incoming</em> records when
*   its queue is full, this output always keeps the <em>newest</em> records:
*   each {@link #publish(LogRecord)} stores the record and, once capacity is
*   reached, evicts the oldest one. Publishing is constant-time and performs
*   no I/O.
* </p>
*
* <p>This output is intended for production diagnostics rather than persistence:</p>
* <ul>
*   <li>
*       <b>Flight recorder</b>: keep the last N records at a verbose level cheaply,
*       then {@link #dumpTo(LogOutput)} them to a file or console when an error
*       occurs — capturing the lead-up context without writing everything to disk.
*   </li>
*   <li>
*       <b>Recent-logs panel</b>: back a desktop diagnostics view by calling
*       {@link #snapshot()} on demand.
*   </li>
*   <li>
*       <b>Troubleshooting</b>: inspect the last N records interactively without
*       enabling a file or socket output.
*   </li>
* </ul>
*
* <p>
*   {@link #snapshot()} returns an unmodifiable copy of the buffered records in
*   oldest-to-newest order. {@link #dumpTo(LogOutput)} publishes that same
*   snapshot to another output without clearing the buffer.
* </p>
*
* <p>
*   The buffer holds references to each {@link LogRecord}, and a record may carry
*   a {@link Throwable} whose stack-trace and cause chain pin additional objects
*   in memory. Retaining the last N throwables can therefore hold more than the
*   record count alone suggests — keep the capacity modest for verbose levels.
* </p>
*
* <p>
*   {@link #flush()} is a no-op — nothing is buffered toward an external
*   destination. {@link #close()} clears the buffer and rejects further records;
*   after closing, {@link #snapshot()} is empty and {@link #publish(LogRecord)}
*   is silently ignored.
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class MemoryLogOutput implements LogOutput{
    // -- Constants: --------------------------------------------------------
    /** Minimum allowed buffer capacity. **/
    private static final int MIN_CAPACITY = 1;
    /** Default number of records the buffer retains. **/
    private static final int DEFAULT_CAPACITY = 512;
    // -- Fields: -----------------------------------------------------------
    /** Minimum log level required for a record to be retained. **/
    private LogLevel minLevel;
    /** Optional filter applied after the level check; {@code null} means accept all. **/
    private LogFilter filter;
    /** Maximum number of records the buffer retains. **/
    private final int capacity;
    /** Backing ring buffer; slots beyond {@link #count} are {@code null}. **/
    private final LogRecord[] buffer;
    /** Index of the oldest retained record. **/
    private int head;
    /** Number of records currently retained, never greater than {@link #capacity}. **/
    private int count;
    /** Whether this output has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates an in-memory log output from the given builder.
    *
    * @param builder the builder containing output values
    **/
    private MemoryLogOutput(Builder builder){
        this.minLevel = builder.minLevel;
        this.filter = builder.filter;
        this.capacity = builder.capacity;
        this.buffer = new LogRecord[builder.capacity];
        this.head = 0;
        this.count = 0;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the minimum log level required for a record to be retained.
    *
    * @return the minimum log level, never {@code null}
    **/
    public LogLevel getMinLevel(){
        return minLevel;
    }
    /**
    * Returns the optional filter applied after the level check.
    *
    * @return the filter, or {@code null} if no filter is set
    **/
    public LogFilter getFilter(){
        return filter;
    }
    /**
    * Returns the maximum number of records the buffer retains.
    *
    * @return the buffer capacity
    **/
    public int getCapacity(){
        return capacity;
    }
    /**
    * Returns the number of records currently retained.
    *
    * @return the current record count, between {@code 0} and {@link #getCapacity()}
    **/
    public synchronized int size(){
        return count;
    }
    /**
    * Returns {@code true} if the buffer currently holds no records.
    *
    * @return {@code true} if no records are retained, otherwise {@code false}
    **/
    public synchronized boolean isEmpty(){
        return count == 0;
    }
    /**
    * Returns {@code true} if this output is accepting new records.
    *
    * @return {@code true} if this output has not been closed, otherwise {@code false}
    **/
    public boolean isOpen(){
        return !closed;
    }
    // -- Mutator Methods: --------------------------------------------------
    /**
    * Replaces the active minimum log level at runtime.
    * Records below the new level are silently dropped on the next publish.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param minLevel the new minimum level; ignored if {@code null}
    **/
    public synchronized void setMinLevel(LogLevel minLevel){
        if(minLevel == null){
            InternalDiagnostic.warn("MemoryLogOutput.setMinLevel: minLevel is null -> ignored");
            return;
        }
        this.minLevel = minLevel;
    }
    /**
    * Replaces the active filter at runtime.
    * Pass {@code null} to remove the filter and accept all records.
    *
    * @param filter the new filter, or {@code null} to accept all records
    **/
    public synchronized void setFilter(LogFilter filter){
        this.filter = filter;
    }
    /**
    * Removes all retained records, leaving the buffer empty.
    * Releases the buffer's references so retained records become eligible for collection.
    **/
    public synchronized void clear(){
        Arrays.fill(buffer, null);
        head = 0;
        count = 0;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link MemoryLogOutput}.
    *
    * @return a new in-memory log output builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    // -- LogOutput Methods: ------------------------------------------------
    /**
    * Retains the given log record, evicting the oldest retained record if the
    * buffer is already full.
    *
    * <p>
    *   Records below the configured minimum level are silently dropped.
    *   A {@code null} record is ignored with an internal diagnostic warning.
    *   Does nothing if this output has been closed.
    * </p>
    *
    * @param record the log record to retain
    **/
    @Override
    public synchronized void publish(LogRecord record){
        if(closed) return;
        if(record == null){
            InternalDiagnostic.warn("MemoryLogOutput.publish: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        if(filter != null && !filter.accept(record)) return;
        buffer[(head + count) % capacity] = record;
        if(count < capacity) count++;
        else head = (head + 1) % capacity;
    }
    /**
    * No-op — this output retains records in memory and buffers nothing toward an
    * external destination.
    **/
    @Override
    public void flush(){
        // Nothing to flush: records are held in memory, not buffered toward a destination.
    }
    /**
    * Clears the buffer and marks this output as closed.
    *
    * <p>
    *   Subsequent calls to {@link #publish(LogRecord)} are silently ignored and
    *   {@link #snapshot()} returns an empty list. This method is idempotent —
    *   calling it more than once has no effect.
    * </p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        closed = true;
        Arrays.fill(buffer, null);
        head = 0;
        count = 0;
    }
    // -- Buffer Methods: ---------------------------------------------------
    /**
    * Returns an unmodifiable snapshot of the retained records, ordered from
    * oldest to newest.
    *
    * <p>
    *   The returned list is a copy taken at the moment of the call — subsequent
    *   publishes are not reflected in it. Returns an empty list if the buffer is
    *   empty or this output has been closed.
    * </p>
    *
    * @return an unmodifiable list of retained records, oldest first, never {@code null}
    **/
    public synchronized List<LogRecord> snapshot(){
        List<LogRecord> records = new ArrayList<>(count);
        for(int index = 0; index < count; index++) records.add(buffer[(head + index) % capacity]);
        return Collections.unmodifiableList(records);
    }
    /**
    * Publishes a snapshot of the retained records to the given output, ordered
    * from oldest to newest. The buffer itself is left unchanged.
    *
    * <p>
    *   Useful for flight-recorder style diagnostics: when an error occurs, dump
    *   the recent in-memory context to a file or console output. The snapshot is
    *   taken under lock and then forwarded without holding the lock, so the
    *   target's own I/O does not block concurrent publishers. Each record is
    *   subject to the target's own level and filter.
    * </p>
    *
    * <p>If {@code target} is {@code null}, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param target the output that receives the buffered records; ignored if {@code null}
    **/
    public void dumpTo(LogOutput target){
        if(target == null){
            InternalDiagnostic.warn("MemoryLogOutput.dumpTo: target is null -> ignored");
            return;
        }
        for(LogRecord record : snapshot()) target.publish(record);
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link MemoryLogOutput} instances.
    *
    * <p>
    *   Defaults: minimum level {@link LogLevel#TRACE}, no filter, and capacity {@code 512}.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Minimum level assigned to the output being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Optional filter assigned to the output being built; {@code null} means accept all. **/
        private LogFilter filter = null;
        /** Buffer capacity assigned to the output being built. **/
        private int capacity = DEFAULT_CAPACITY;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the minimum log level required for a record to be retained.
        * Records below this level are silently dropped.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default minimum level is used.</p>
        *
        * @param minLevel the minimum log level, or {@code null} to use the default minimum level
        * @return this builder
        **/
        public Builder minLevel(LogLevel minLevel){
            if(minLevel == null){
                InternalDiagnostic.warn("MemoryLogOutput.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
                minLevel = DEFAULT_MIN_LEVEL;
            }
            this.minLevel = minLevel;
            return this;
        }
        /**
        * Sets an optional filter applied after the level check.
        * Records for which {@link LogFilter#accept(LogRecord)} returns {@code false}
        * are silently dropped. Pass {@code null} to remove a previously set filter.
        *
        * @param filter the filter to apply, or {@code null} to accept all records
        * @return this builder
        **/
        public Builder filter(LogFilter filter){
            this.filter = filter;
            return this;
        }
        /**
        * Sets the maximum number of records the buffer retains before the oldest
        * record is overwritten.
        *
        * <p>If a value less than {@link #MIN_CAPACITY} is passed, an internal diagnostic warning is emitted and the minimum capacity is used.</p>
        *
        * @param capacity the maximum number of retained records, or a value less than {@link #MIN_CAPACITY} to use the minimum capacity
        * @return this builder
        **/
        public Builder capacity(int capacity){
            if(capacity < MIN_CAPACITY){
                InternalDiagnostic.warn(
                    "MemoryLogOutput.Builder.capacity: capacity (" + capacity + ") is too small -> using MIN_CAPACITY=" + MIN_CAPACITY
                );
                this.capacity = MIN_CAPACITY;
            }else this.capacity = capacity;
            return this;
        }
        /**
        * Builds a new {@link MemoryLogOutput}.
        *
        * @return a new in-memory log output
        **/
        public MemoryLogOutput build(){
            return new MemoryLogOutput(this);
        }
    }
}