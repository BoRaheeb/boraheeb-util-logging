package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
// -- AsyncLogOutput Class: ---------------------------------------------
/**
* A {@link LogOutput} that publishes log records asynchronously via a
* background thread, decoupling calling threads from I/O latency.
*
* <p>
*   Each call to {@link #publish(LogRecord)} enqueues the record and returns
*   immediately. A single background daemon thread drains the queue and
*   forwards records to the wrapped {@link LogOutput} delegate.
* </p>
*
* <p>
*   Behaviour when the internal queue is full is governed by the configured
*   {@link OverflowPolicy}: by default ({@link OverflowPolicy#DROP_NEWEST}) the
*   incoming record is dropped and an internal diagnostic warning is emitted, so
*   callers are never blocked. {@link OverflowPolicy#DROP_OLDEST} instead evicts
*   the oldest queued record to make room, while {@link OverflowPolicy#BLOCK}
*   applies backpressure by blocking the caller until space becomes available.
* </p>
*
* <p>
*   {@link #flush()} flushes the delegate directly. Records still waiting in
*   the queue at flush time may not be included — call {@link #close()} for a
*   best-effort full drain before shutdown, bounded by the configured shutdown timeout.
* </p>
*
* <p>
*   {@link #close()} stops accepting new records, interrupts the background
*   thread so it can drain any remaining queued records (if {@code drainOnClose}
*   is enabled) and exit, waits up to the configured shutdown timeout for that
*   to complete, then flushes and closes the delegate. If the worker is still
*   draining when the timeout expires (or {@code shutdownTimeoutMs} is {@code 0},
*   which waits not at all), the delegate is closed anyway and any records still
*   queued are abandoned — an internal diagnostic error reports this so it is
*   never silent.
* </p>
*
* <p>
*   If the delegate violates the {@link LogOutput} contract and throws a
*   {@link RuntimeException} from {@code publish}, the worker thread catches it,
*   counts it as a delivery failure, reports it through the internal diagnostics,
*   and continues with the next record rather than dying. {@link Error}s are not
*   caught and will terminate the worker.
* </p>
*
* <p>
*   Lightweight operational counters are exposed through {@link #stats()} as an
*   immutable {@link Stats} snapshot: accepted, delivered, dropped, deliveryFailed,
*   blocked, pending, and peak pending. They let a caller see whether asynchronous
*   logging is healthy — records being dropped, the queue backing up, or the
*   delegate failing. When the delegate honors the {@link LogOutput} contract, the
*   counters satisfy {@code accepted ≈ delivered + dropped + deliveryFailed + pending}
*   (approximate because the counters are sampled independently, not under one lock).
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class AsyncLogOutput implements LogOutput{
    // -- Constants: --------------------------------------------------------
    /** Minimum allowed queue capacity. **/
    private static final int MIN_QUEUE_CAPACITY = 16;
    /** Default maximum number of records the queue can hold. **/
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;
    /**
    * Interval at which a {@link OverflowPolicy#BLOCK} publish re-checks {@link #closed}
    * while waiting for queue space, so a blocked caller cannot wait past the output being
    * closed instead of blocking indefinitely on a single {@code queue.put}.
    **/
    private static final long BLOCK_POLL_MS = 100L;
    /** Default policy applied when a record is published while the queue is full. **/
    private static final OverflowPolicy DEFAULT_OVERFLOW_POLICY = OverflowPolicy.DROP_NEWEST;
    /** Minimum allowed shutdown timeout in milliseconds. **/
    private static final int MIN_SHUTDOWN_TIMEOUT_MS = 0;
    /** Default time in milliseconds to wait for the worker thread to finish on close. **/
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_MS = 5000;
    /** Name of the background worker thread. **/
    private static final String WORKER_THREAD_NAME = "boraheeb-async-log";
    // -- OverflowPolicy Enum: ----------------------------------------------
    /** Strategy applied when a record is published while the queue is full. **/
    public enum OverflowPolicy{
        /** Block the calling thread until queue space becomes available, so no record is lost. **/
        BLOCK,
        /** Drop the incoming record and emit an internal diagnostic warning, without blocking the caller. **/
        DROP_NEWEST,
        /** Evict the oldest queued record to make room for the incoming one and emit an internal diagnostic warning, without blocking the caller. **/
        DROP_OLDEST
    }
    // -- Fields: -----------------------------------------------------------
    /** Wrapped output that receives records from the background thread. **/
    private final LogOutput delegate;
    /** Minimum log level required for a record to be enqueued. **/
    private volatile LogLevel minLevel;
    /** Optional filter applied after the level check; {@code null} means accept all. **/
    private volatile LogFilter filter;
    /** Maximum number of records the queue can hold. **/
    private final int capacity;
    /** Policy applied when a record is published while the queue is full. **/
    private final OverflowPolicy overflowPolicy;
    /** Time in milliseconds to wait for the worker thread to finish on close. **/
    private final int shutdownTimeoutMs;
    /** Queue that buffers log records between calling threads and the worker thread. **/
    private final BlockingQueue<LogRecord> queue;
    /** Count of records that passed the level and filter checks and were taken for delivery. **/
    private final LongAdder accepted = new LongAdder();
    /** Count of records successfully forwarded to the delegate. **/
    private final LongAdder delivered = new LongAdder();
    /** Count of records discarded by the overflow policy without delivery. **/
    private final LongAdder dropped = new LongAdder();
    /** Count of records the delegate failed to publish by throwing a runtime exception. **/
    private final LongAdder deliveryFailed = new LongAdder();
    /** Count of times a {@link OverflowPolicy#BLOCK} publish found the queue full and had to wait. **/
    private final LongAdder blocked = new LongAdder();
    /** High-water mark of the queue size observed after enqueue. **/
    private final AtomicInteger peakPending = new AtomicInteger(0);
    /** Whether to drain remaining queued records when this output is closed. **/
    private final boolean drainOnClose;
    /** Background thread that drains the queue and forwards records to the delegate. **/
    private final Thread workerThread;
    /** Whether this output has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates an async log output from the given builder.
    *
    * @param builder the builder containing output values
    **/
    private AsyncLogOutput(Builder builder){
        this.delegate = builder.delegate;
        this.minLevel = builder.minLevel;
        this.filter = builder.filter;
        this.capacity = builder.queueCapacity;
        this.overflowPolicy = builder.overflowPolicy;
        this.shutdownTimeoutMs = builder.shutdownTimeoutMs;
        this.queue = new LinkedBlockingQueue<>(builder.queueCapacity);
        this.drainOnClose = builder.drainOnClose;
        this.workerThread = new Thread(this::drainLoop, WORKER_THREAD_NAME);
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the wrapped output that receives records from the background thread.
    *
    * @return the delegate log output, never {@code null}
    **/
    public LogOutput getDelegate(){
        return delegate;
    }
    /**
    * Returns the minimum log level required for a record to be enqueued.
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
    * Returns the maximum number of records the internal queue can hold.
    *
    * @return the queue capacity
    **/
    public int getQueueCapacity(){
        return capacity;
    }
    /**
    * Returns the policy applied when a record is published while the queue is full.
    *
    * @return the overflow policy, never {@code null}
    **/
    public OverflowPolicy getOverflowPolicy(){
        return overflowPolicy;
    }
    /**
    * Returns the time in milliseconds to wait for the background thread to
    * finish draining when this output is closed.
    *
    * @return the shutdown timeout in milliseconds
    **/
    public int getShutdownTimeoutMs(){
        return shutdownTimeoutMs;
    }
    /**
    * Returns the current number of records waiting in the queue.
    *
    * @return the number of pending records
    **/
    public int getPendingCount(){
        return queue.size();
    }
    /**
    * Returns a point-in-time snapshot of this output's operational counters.
    *
    * @return an immutable {@link Stats} snapshot, never {@code null}
    **/
    public Stats stats(){
        return new Stats(
            accepted.sum(), delivered.sum(), dropped.sum(), deliveryFailed.sum(),
            blocked.sum(), queue.size(), peakPending.get()
        );
    }
    /**
    * Returns whether remaining queued records are drained when this output is closed.
    *
    * @return {@code true} if records are drained on close, otherwise {@code false}
    **/
    public boolean isDrainOnClose(){
        return drainOnClose;
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
    * Records below the new level are silently dropped before entering the queue.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param minLevel the new minimum level; ignored if {@code null}
    **/
    public void setMinLevel(LogLevel minLevel){
        if(minLevel == null){
            InternalDiagnostic.warn("AsyncLogOutput.setMinLevel: minLevel is null -> ignored");
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
    public void setFilter(LogFilter filter){
        this.filter = filter;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing an {@link AsyncLogOutput}.
    *
    * @param delegate the wrapped output that receives published records; must not be {@code null}
    * @return a new async log output builder
    **/
    public static Builder builder(LogOutput delegate){
        return new Builder(delegate);
    }
    // -- LogOutput Methods: ------------------------------------------------
    /**
    * Enqueues the given log record for asynchronous publishing.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * <p>
    *   Behaviour when the queue is full depends on the configured
    *   {@link OverflowPolicy}: {@link OverflowPolicy#BLOCK} blocks the caller
    *   until space becomes available, while {@link OverflowPolicy#DROP_NEWEST}
    *   and {@link OverflowPolicy#DROP_OLDEST} drop a record and emit an internal
    *   diagnostic warning without ever blocking. Does nothing if this output has
    *   been closed.
    * </p>
    *
    * @param record the log record to publish
    **/
    @Override
    public void publish(LogRecord record){
        if(closed) return;
        if(record == null){
            InternalDiagnostic.warn("AsyncLogOutput.publish: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        if(filter != null && !filter.accept(record)) return;
        accepted.increment();
        switch(overflowPolicy){
            case BLOCK -> enqueueBlocking(record);
            case DROP_NEWEST -> enqueueDroppingNewest(record);
            case DROP_OLDEST -> enqueueDroppingOldest(record);
        }
        peakPending.accumulateAndGet(queue.size(), Math::max);
    }
    /**
    * Flushes the delegate output directly.
    *
    * <p>
    *   Only records already forwarded to the delegate by the background thread
    *   are affected. Records still in the queue have not yet reached the delegate
    *   and are not included. Draining the queue here would require the calling
    *   thread to publish to the delegate concurrently with the worker — use
    *   {@link #close()} for a best-effort full drain before shutdown, bounded by
    *   the configured shutdown timeout.
    * </p>
    **/
    @Override
    public void flush(){
        if(closed) return;
        delegate.flush();
    }
    /**
    * Stops accepting new records, interrupts the background thread, waits up
    * to the configured shutdown timeout for it to finish draining and exit,
    * then flushes and closes the delegate.
    *
    * <p>
    *   The background thread is responsible for draining any remaining queued
    *   records (if {@code drainOnClose} is enabled) before it exits, so only
    *   one thread ever calls the delegate at a time. This makes the drain a
    *   <b>best-effort guarantee bounded by {@code shutdownTimeoutMs}</b>, not an
    *   unconditional one: if the timeout expires before the worker finishes —
    *   or {@code shutdownTimeoutMs} is {@code 0}, which does not wait at all —
    *   the worker is abandoned mid-drain and this method proceeds to flush and
    *   close the delegate regardless. Any records still queued at that point can
    *   never be delivered, since the worker is racing an already-closing delegate.
    *   When this happens, an internal diagnostic error is emitted reporting the
    *   approximate number of abandoned records, so record loss on shutdown is
    *   never silent even though it is not prevented.
    * </p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        closed = true;
        workerThread.interrupt();
        try{
            if(shutdownTimeoutMs > 0) workerThread.join(shutdownTimeoutMs);
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
        }
        if(workerThread.isAlive())
            InternalDiagnostic.error(
                "AsyncLogOutput.close: worker still draining after " + shutdownTimeoutMs +
                "ms shutdown timeout -> ~" + queue.size() + " queued records abandoned, delegate closing now"
            );
        delegate.flush();
        delegate.close();
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Enqueues the given record, blocking the calling thread until queue space
    * becomes available.
    *
    * <p>
    *   Waits in {@link #BLOCK_POLL_MS} increments rather than indefinitely, so
    *   this method never blocks past this output being closed: if {@link #close()}
    *   runs while a caller is waiting here — for example because the worker thread
    *   exits without draining ({@code drainOnClose(false)}, or the shutdown timeout
    *   expires) — there may be no consumer left to free up queue space, and an
    *   unconditional wait would then block the caller forever. Polling lets the
    *   wait notice {@link #closed} and give up instead.
    * </p>
    *
    * <p>
    *   If the thread is interrupted while waiting, the interrupt flag is restored,
    *   an internal diagnostic warning is emitted, and the record is dropped. If this
    *   output is closed while waiting, an internal diagnostic warning is emitted and
    *   the record is dropped.
    * </p>
    *
    * @param record the record to enqueue
    **/
    private void enqueueBlocking(LogRecord record){
        if(queue.offer(record)) return;
        blocked.increment();
        try{
            while(!queue.offer(record, BLOCK_POLL_MS, TimeUnit.MILLISECONDS)){
                if(closed){
                    dropped.increment();
                    InternalDiagnostic.warn("AsyncLogOutput.publish: output closed while waiting for queue space -> record dropped");
                    return;
                }
            }
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            dropped.increment();
            InternalDiagnostic.warn("AsyncLogOutput.publish: interrupted while waiting for queue space -> record dropped");
        }
    }
    /**
    * Enqueues the given record without blocking.
    * If the queue is full, the incoming record is dropped and an internal
    * diagnostic warning is emitted.
    *
    * @param record the record to enqueue
    **/
    private void enqueueDroppingNewest(LogRecord record){
        if(!queue.offer(record)){
            dropped.increment();
            InternalDiagnostic.warn("AsyncLogOutput.publish: queue is full (capacity=" + capacity + ") -> newest record dropped");
        }
    }
    /**
    * Enqueues the given record without blocking, evicting the oldest queued
    * records to make room when the queue is full.
    *
    * <p>
    *   Each eviction emits an internal diagnostic warning. The loop retries until
    *   the record is accepted, so it always makes progress even when other threads
    *   publish concurrently.
    * </p>
    *
    * @param record the record to enqueue
    **/
    private void enqueueDroppingOldest(LogRecord record){
        while(!queue.offer(record))
            if(queue.poll() != null){
                dropped.increment();
                InternalDiagnostic.warn("AsyncLogOutput.publish: queue is full (capacity=" + capacity + ") -> oldest record dropped");
            }
    }
    /**
    * Forwards a single record to the delegate, counting the outcome.
    *
    * <p>
    *   On normal return, {@code delivered} is incremented. If the delegate violates
    *   the {@link LogOutput} contract and throws a {@link RuntimeException}, it is
    *   caught, {@code deliveryFailed} is incremented, the failure is reported through
    *   the internal diagnostics, and the worker continues with the next record.
    *   {@link Error}s are not caught and will terminate the worker.
    * </p>
    *
    * @param record the record to forward to the delegate
    **/
    private void deliver(LogRecord record){
        try{
            delegate.publish(record);
            delivered.increment();
        }catch(RuntimeException ex){
            deliveryFailed.increment();
            InternalDiagnostic.error(
                "AsyncLogOutput.deliver: delegate failed to publish -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> record dropped, worker continues"
            );
        }
    }
    /**
    * Background thread loop: takes records from the queue and forwards them to the
    * delegate, blocking while the queue is empty. When interrupted, drains any
    * remaining queued records if {@code drainOnClose} is enabled, then exits.
    *
    * <p>
    *   Each forward goes through {@link #deliver(LogRecord)}, which guards the
    *   delegate call so a contract-violating delegate cannot kill the worker.
    * </p>
    **/
    private void drainLoop(){
        while(!Thread.currentThread().isInterrupted()){
            LogRecord record;
            try{
                record = queue.take();
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
                continue;
            }
            deliver(record);
        }
        if(drainOnClose){
            LogRecord record;
            while((record = queue.poll()) != null) deliver(record);
        }
    }
    // -- Stats: ------------------------------------------------------------
    /**
    * An immutable, point-in-time snapshot of an {@link AsyncLogOutput}'s
    * operational counters, obtained from {@link AsyncLogOutput#stats()}.
    *
    * <p>
    *   The counters are cumulative for the life of the output — there is no reset.
    *   They are sampled independently rather than under a single lock, so the
    *   snapshot is near-instantaneous and its fields may differ by a few in-flight
    *   records. When the delegate honors the {@link LogOutput} contract, the counters
    *   satisfy {@code accepted ≈ delivered + dropped + deliveryFailed + pending}.
    * </p>
    **/
    public static final class Stats{
        // -- Stats Fields: -----------------------------------------------------
        /** Records that passed the level and filter checks and were taken for delivery. **/
        private final long accepted;
        /** Records successfully forwarded to the delegate. **/
        private final long delivered;
        /** Records discarded by the overflow policy without delivery. **/
        private final long dropped;
        /** Records the delegate failed to publish by throwing a runtime exception. **/
        private final long deliveryFailed;
        /** Times a {@link OverflowPolicy#BLOCK} publish found the queue full and had to wait. **/
        private final long blocked;
        /** Records waiting in the queue at the moment of the snapshot. **/
        private final int pending;
        /** High-water mark of the queue size observed since this output was created. **/
        private final int peakPending;
        // -- Stats Constructors: -----------------------------------------------
        /**
        * Creates a stats snapshot with the given counter values.
        *
        * @param accepted records taken for delivery
        * @param delivered records forwarded to the delegate
        * @param dropped records discarded by the overflow policy
        * @param deliveryFailed records the delegate failed to publish
        * @param blocked times a blocking publish had to wait
        * @param pending records waiting in the queue
        * @param peakPending high-water mark of the queue size
        **/
        private Stats(
            long accepted, long delivered, long dropped, long deliveryFailed,
            long blocked, int pending, int peakPending
        ){
            this.accepted = accepted;
            this.delivered = delivered;
            this.dropped = dropped;
            this.deliveryFailed = deliveryFailed;
            this.blocked = blocked;
            this.pending = pending;
            this.peakPending = peakPending;
        }
        // -- Stats Accessor Methods: -------------------------------------------
        /**
        * Returns the number of records that passed the level and filter checks
        * and were taken for asynchronous delivery.
        *
        * <p>An accepted record may still be dropped by the overflow policy — this is an admission count, not a delivery guarantee.</p>
        *
        * @return the accepted record count
        **/
        public long getAccepted(){
            return accepted;
        }
        /**
        * Returns the number of records successfully forwarded to the delegate.
        *
        * @return the delivered record count
        **/
        public long getDelivered(){
            return delivered;
        }
        /**
        * Returns the number of records discarded by the overflow policy without delivery.
        *
        * @return the dropped record count
        **/
        public long getDropped(){
            return dropped;
        }
        /**
        * Returns the number of records the delegate failed to publish by throwing
        * a runtime exception.
        *
        * @return the delivery-failed record count
        **/
        public long getDeliveryFailed(){
            return deliveryFailed;
        }
        /**
        * Returns the number of times a {@link OverflowPolicy#BLOCK} publish found
        * the queue full and had to wait for space.
        *
        * @return the blocked publish count
        **/
        public long getBlocked(){
            return blocked;
        }
        /**
        * Returns the number of records waiting in the queue at the moment of the snapshot.
        *
        * @return the pending record count
        **/
        public int getPending(){
            return pending;
        }
        /**
        * Returns the high-water mark of the queue size observed since this output was created.
        *
        * @return the peak pending record count
        **/
        public int getPeakPending(){
            return peakPending;
        }
        // -- Stats Utility Methods: --------------------------------------------
        /**
        * Returns a human-readable representation of this snapshot.
        *
        * @return a string describing every counter value, never {@code null}
        **/
        @Override
        public String toString(){
            return "AsyncLogOutput.Stats[accepted=" + accepted + ", delivered=" + delivered +
                ", dropped=" + dropped + ", deliveryFailed=" + deliveryFailed + ", blocked=" + blocked +
                ", pending=" + pending + ", peakPending=" + peakPending + "]";
        }
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link AsyncLogOutput} instances.
    *
    * <p>
    *   Defaults: minimum level {@link LogLevel#TRACE}, no filter, queue capacity {@code 1024},
    *   overflow policy {@link OverflowPolicy#DROP_NEWEST}, shutdown timeout {@code 5000} ms,
    *   drain on close enabled.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Wrapped output assigned to the async output being built. **/
        private final LogOutput delegate;
        /** Minimum level assigned to the output being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Optional filter assigned to the output being built; {@code null} means accept all. **/
        private LogFilter filter = null;
        /** Queue capacity assigned to the output being built. **/
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
        /** Overflow policy assigned to the output being built. **/
        private OverflowPolicy overflowPolicy = DEFAULT_OVERFLOW_POLICY;
        /** Shutdown timeout assigned to the output being built. **/
        private int shutdownTimeoutMs = DEFAULT_SHUTDOWN_TIMEOUT_MS;
        /** Whether to drain remaining records when the output is closed. **/
        private boolean drainOnClose = true;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with the required delegate output.
        *
        * @param delegate the wrapped output that receives published records
        **/
        private Builder(LogOutput delegate){
            if(delegate == null)
                throw new IllegalArgumentException("AsyncLogOutput.Builder: delegate must not be null");
            this.delegate = delegate;
        }
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the minimum log level required for a record to be enqueued.
        * Records below this level are silently dropped before entering the queue.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default minimum level is used.</p>
        *
        * @param minLevel the minimum log level, or {@code null} to use the default minimum level
        * @return this builder
        **/
        public Builder minLevel(LogLevel minLevel){
            if(minLevel == null){
                InternalDiagnostic.warn("AsyncLogOutput.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
                minLevel = DEFAULT_MIN_LEVEL;
            }
            this.minLevel = minLevel;
            return this;
        }
        /**
        * Sets an optional filter applied after the level check.
        * Records for which {@link LogFilter#accept(LogRecord)} returns {@code false}
        * are silently dropped before entering the queue. Pass {@code null} to remove
        * a previously set filter.
        *
        * @param filter the filter to apply, or {@code null} to accept all records
        * @return this builder
        **/
        public Builder filter(LogFilter filter){
            this.filter = filter;
            return this;
        }
        /**
        * Sets the maximum number of records the internal queue can hold before
        * incoming records are dropped.
        *
        * <p>If a value less than {@link #MIN_QUEUE_CAPACITY} is passed, an internal diagnostic warning is emitted and the minimum queue capacity is used.</p>
        *
        * @param capacity the maximum queue size, or a value less than {@link #MIN_QUEUE_CAPACITY} to use the minimum queue capacity
        * @return this builder
        **/
        public Builder queueCapacity(int capacity){
            if(capacity < MIN_QUEUE_CAPACITY){
                InternalDiagnostic.warn(
                    "AsyncLogOutput.Builder.queueCapacity: capacity (" + capacity + ") is too small -> using MIN_QUEUE_CAPACITY=" + MIN_QUEUE_CAPACITY
                );
                queueCapacity = MIN_QUEUE_CAPACITY;
            }else queueCapacity = capacity;
            return this;
        }
        /**
        * Sets the policy applied when a record is published while the queue is full.
        *
        * <p>
        *   {@link OverflowPolicy#BLOCK} blocks the calling thread until space is
        *   available, applying backpressure so no record is lost.
        *   {@link OverflowPolicy#DROP_NEWEST} (the default) drops the incoming
        *   record, and {@link OverflowPolicy#DROP_OLDEST} evicts the oldest queued
        *   record to make room for the incoming one; both emit an internal
        *   diagnostic warning and never block the caller.
        * </p>
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default overflow policy is used.</p>
        *
        * @param policy the overflow policy, or {@code null} to use the default overflow policy
        * @return this builder
        **/
        public Builder overflowPolicy(OverflowPolicy policy){
            if(policy == null){
                InternalDiagnostic.warn("AsyncLogOutput.Builder.overflowPolicy: policy is null -> using DEFAULT_OVERFLOW_POLICY=" + DEFAULT_OVERFLOW_POLICY);
                policy = DEFAULT_OVERFLOW_POLICY;
            }
            this.overflowPolicy = policy;
            return this;
        }
        /**
        * Sets how long to wait for the background thread to finish when closing.
        * A value of {@code 0} means do not wait at all.
        *
        * <p>If a negative value is passed, an internal diagnostic warning is emitted and the default shutdown timeout is used.</p>
        *
        * @param ms the wait time in milliseconds, or negative to use the default shutdown timeout
        * @return this builder
        **/
        public Builder shutdownTimeoutMs(int ms){
            if(ms < MIN_SHUTDOWN_TIMEOUT_MS){
                InternalDiagnostic.warn(
                    "AsyncLogOutput.Builder.shutdownTimeoutMs: ms (" + ms + ") is negative -> using DEFAULT_SHUTDOWN_TIMEOUT_MS=" + DEFAULT_SHUTDOWN_TIMEOUT_MS
                );
                shutdownTimeoutMs = DEFAULT_SHUTDOWN_TIMEOUT_MS;
            }else shutdownTimeoutMs = ms;
            return this;
        }
        /**
        * Sets whether to drain remaining queued records when this output is closed.
        *
        * <p>
        *   When enabled (the default), the background thread publishes any records
        *   still in the queue before it exits, so they are delivered before the
        *   delegate is closed. This is a <b>best-effort guarantee bounded by
        *   {@link #shutdownTimeoutMs(int)}</b>, not an unconditional one: queued
        *   records are drained before the delegate is closed only if the worker
        *   finishes within the configured shutdown timeout. If the timeout expires
        *   first (or is {@code 0}), {@link AsyncLogOutput#close()} closes the delegate
        *   anyway and reports the abandoned records through an internal diagnostic
        *   error rather than losing them silently.
        * </p>
        *
        * @param drainOnClose {@code true} to drain on close, {@code false} to discard
        * @return this builder
        **/
        public Builder drainOnClose(boolean drainOnClose){
            this.drainOnClose = drainOnClose;
            return this;
        }
        /**
        * Builds a new {@link AsyncLogOutput} and starts the background worker thread.
        *
        * @return a new async log output
        **/
        public AsyncLogOutput build(){
            return new AsyncLogOutput(this);
        }
    }
}