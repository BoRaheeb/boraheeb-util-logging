package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.AsyncLogOutput;
import boraheeb.util.logging.AsyncLogOutput.OverflowPolicy;
import boraheeb.util.logging.AsyncLogOutput.Stats;
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogOutput;
import boraheeb.util.logging.LogRecord;
// -- AsyncBenchmark Class: ----------------------------------------------
/**
* Standalone benchmark measuring {@link AsyncLogOutput} throughput and
* overflow behavior under each {@link OverflowPolicy}, against a
* deliberately slow downstream delegate so the queue experiences real
* backpressure.
*
* <p>Not a JUnit test — run directly via {@code main}.</p>
**/
public final class AsyncBenchmark{

    private AsyncBenchmark(){}

    /** Number of records published per scenario. **/
    private static final int RECORD_COUNT = 200_000;
    /** Queue capacity used for every scenario, small enough to force overflow at high producer rates. **/
    private static final int QUEUE_CAPACITY = 1024;
    /** Artificial per-record delay applied by the slow delegate, simulating I/O latency. **/
    private static final long DELEGATE_DELAY_NANOS = 20_000L; // 20 us/record -> ~50,000 rec/s max delegate throughput
    /** Number of records used for a brief JIT warmup pass before each timed scenario. **/
    private static final int WARMUP_RECORDS = 5_000;

    public static void main(String[] args){
        BenchSupport.printHeader("AsyncBenchmark: AsyncLogOutput throughput & overflow behaviour by policy");
        BenchSupport.metric("Record count per scenario", RECORD_COUNT);
        BenchSupport.metric("Queue capacity", QUEUE_CAPACITY);
        BenchSupport.metric("Simulated delegate delay", DELEGATE_DELAY_NANOS + " ns/record");

        for(OverflowPolicy policy : OverflowPolicy.values())
            runScenario(policy);

        BenchSupport.printSection("Interpretation");
        System.out.println("  BLOCK guarantees zero loss (dropped=0) but caps producer throughput at");
        System.out.println("  roughly the delegate's own rate, since the caller is backpressured.");
        System.out.println("  DROP_NEWEST/DROP_OLDEST let the producer run at full speed, shedding load");
        System.out.println("  instead of blocking -- visible below as a much higher producer throughput");
        System.out.println("  paired with a large 'dropped' count.");
    }

    private static void runScenario(OverflowPolicy policy){
        BenchSupport.printSection("Overflow policy: " + policy);

        // Warmup: exercise the same code path briefly so JIT compiles the hot methods
        // before the timed run. Uses its own short-lived output so it does not pollute stats.
        try(SlowLogOutput warmDelegate = new SlowLogOutput(DELEGATE_DELAY_NANOS);
            AsyncLogOutput warmAsync = AsyncLogOutput.builder(warmDelegate)
                .queueCapacity(QUEUE_CAPACITY)
                .overflowPolicy(policy)
                .shutdownTimeoutMs(1000)
                .build()){
            for(int i = 0; i < WARMUP_RECORDS; i++) warmAsync.publish(sampleRecord(i));
        }

        SlowLogOutput delegate = new SlowLogOutput(DELEGATE_DELAY_NANOS);
        AsyncLogOutput async = AsyncLogOutput.builder(delegate)
            .queueCapacity(QUEUE_CAPACITY)
            .overflowPolicy(policy)
            .shutdownTimeoutMs(10_000)
            .build();

        long start = System.nanoTime();
        for(int i = 0; i < RECORD_COUNT; i++) async.publish(sampleRecord(i));
        long publishElapsed = System.nanoTime() - start;

        // close() drains (best-effort) and blocks until the worker finishes or times out.
        async.close();
        long totalElapsed = System.nanoTime() - start;

        Stats stats = async.stats();
        BenchSupport.metricThroughput("Producer-side publish() throughput", RECORD_COUNT, publishElapsed);
        BenchSupport.metric("Total elapsed (incl. drain on close)", BenchSupport.formatDuration(totalElapsed));
        BenchSupport.metric("Delegate records actually written", delegate.count());
        BenchSupport.metric("Stats.accepted", stats.getAccepted());
        BenchSupport.metric("Stats.delivered", stats.getDelivered());
        BenchSupport.metric("Stats.dropped", stats.getDropped());
        BenchSupport.metric("Stats.deliveryFailed", stats.getDeliveryFailed());
        BenchSupport.metric("Stats.blocked", stats.getBlocked());
        BenchSupport.metric("Stats.pending (post-close)", stats.getPending());
        BenchSupport.metric("Stats.peakPending", stats.getPeakPending());
    }

    private static LogRecord sampleRecord(int i){
        return LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("AsyncBenchmark")
            .sourceName("AsyncBenchmark")
            .message("benchmark record #" + i)
            .build();
    }

    /**
    * A {@link LogOutput} that simulates slow I/O by busy-waiting a fixed number of
    * nanoseconds per {@code publish}, so a downstream {@link AsyncLogOutput}'s queue
    * experiences genuine backpressure rather than draining instantly.
    **/
    private static final class SlowLogOutput implements LogOutput{
        private final long delayNanos;
        private final java.util.concurrent.atomic.LongAdder counter = new java.util.concurrent.atomic.LongAdder();
        private volatile boolean closed = false;

        SlowLogOutput(long delayNanos){
            this.delayNanos = delayNanos;
        }

        @Override
        public void publish(LogRecord record){
            if(closed) return;
            long deadline = System.nanoTime() + delayNanos;
            while(System.nanoTime() < deadline){
                // Busy-wait rather than Thread.sleep: sleep's ~1ms granularity would
                // swamp a 20us target delay and distort the intended delegate rate.
            }
            counter.increment();
        }

        @Override
        public void flush(){ /* no-op */ }

        @Override
        public void close(){
            closed = true;
        }

        @Override
        public boolean isOpen(){
            return !closed;
        }

        long count(){
            return counter.sum();
        }
    }
}
