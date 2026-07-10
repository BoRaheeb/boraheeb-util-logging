package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogOutput;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.MemoryLogOutput;

import java.util.List;
// -- MemoryBenchmark Class: ---------------------------------------------
/**
* Standalone benchmark measuring {@link MemoryLogOutput} ring-buffer publish
* throughput, {@code snapshot()}/{@code dumpTo()} cost, and actual JVM heap
* growth for holding a full buffer of records with small vs. larger
* structured-field payloads.
*
* <p>Not a JUnit test -- run directly via {@code main}.</p>
**/
public final class MemoryBenchmark{

    private MemoryBenchmark(){}

    /** Capacities exercised for the publish-throughput scenario. **/
    private static final int[] CAPACITIES = {100, 10_000, 1_000_000};
    /** Number of publishes performed per capacity (chosen so smaller capacities wrap many times over). **/
    private static final int PUBLISH_COUNT = 2_000_000;
    /** Number of warmup publishes before each timed run. **/
    private static final int WARMUP_COUNT = 20_000;
    /** Capacity used for the heap-footprint comparison. **/
    private static final int HEAP_CAPACITY = 200_000;
    /** Number of "larger payload" structured fields attached per record in the heap comparison. **/
    private static final int LARGE_FIELD_COUNT = 8;
    /** Approximate length of each large-payload field value, in characters. **/
    private static final int LARGE_FIELD_VALUE_LENGTH = 256;

    public static void main(String[] args){
        BenchSupport.printHeader("MemoryBenchmark: MemoryLogOutput ring-buffer cost & heap footprint");

        for(int capacity : CAPACITIES) benchmarkPublish(capacity);

        benchmarkSnapshotAndDump();

        benchmarkHeapFootprint("small payload (no structured fields)", MemoryBenchmark::sampleRecordSmall);
        benchmarkHeapFootprint("larger payload (" + LARGE_FIELD_COUNT + " structured fields, ~" +
            LARGE_FIELD_VALUE_LENGTH + " chars each)", MemoryBenchmark::sampleRecordLarge);
    }

    private static void benchmarkPublish(int capacity){
        BenchSupport.printSection("publish() throughput @ capacity=" + String.format("%,d", capacity));

        MemoryLogOutput warm = MemoryLogOutput.builder().capacity(Math.max(1, capacity)).build();
        for(int i = 0; i < WARMUP_COUNT; i++) warm.publish(sampleRecordSmall(i));
        warm.close();

        MemoryLogOutput output = MemoryLogOutput.builder().capacity(capacity).build();
        long start = System.nanoTime();
        for(int i = 0; i < PUBLISH_COUNT; i++) output.publish(sampleRecordSmall(i));
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("publish() throughput", PUBLISH_COUNT, elapsed);
        BenchSupport.metric("Final buffer size", output.size());
        output.close();
    }

    private static void benchmarkSnapshotAndDump(){
        BenchSupport.printSection("snapshot() and dumpTo() cost @ capacity=" + String.format("%,d", HEAP_CAPACITY));

        MemoryLogOutput output = MemoryLogOutput.builder().capacity(HEAP_CAPACITY).build();
        for(int i = 0; i < HEAP_CAPACITY; i++) output.publish(sampleRecordSmall(i));

        // Warmup snapshot/dump path.
        output.snapshot();
        NoOpLogOutput warmSink = new NoOpLogOutput();
        output.dumpTo(warmSink);

        long snapStart = System.nanoTime();
        List<LogRecord> snapshot = output.snapshot();
        long snapElapsed = System.nanoTime() - snapStart;
        BenchSupport.metric("snapshot() size", snapshot.size());
        BenchSupport.metricThroughput("snapshot() copy throughput", snapshot.size(), snapElapsed);

        NoOpLogOutput sink = new NoOpLogOutput();
        long dumpStart = System.nanoTime();
        output.dumpTo(sink);
        long dumpElapsed = System.nanoTime() - dumpStart;
        BenchSupport.metric("dumpTo() records forwarded", sink.count());
        BenchSupport.metricThroughput("dumpTo() forwarding throughput", sink.count(), dumpElapsed);

        output.close();
    }

    private static void benchmarkHeapFootprint(String label, java.util.function.IntFunction<LogRecord> factory){
        BenchSupport.printSection("Heap footprint: " + label + " @ capacity=" + String.format("%,d", HEAP_CAPACITY));

        BenchSupport.settleGc();
        BenchSupport.Snapshot before = BenchSupport.Snapshot.capture();

        MemoryLogOutput output = MemoryLogOutput.builder().capacity(HEAP_CAPACITY).build();
        for(int i = 0; i < HEAP_CAPACITY; i++) output.publish(factory.apply(i));

        BenchSupport.settleGc();
        BenchSupport.Snapshot after = BenchSupport.Snapshot.capture();
        after.printDeltaFrom(before);
        BenchSupport.metric("Records retained", output.size());

        output.close();
    }

    private static LogRecord sampleRecordSmall(int i){
        return LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("MemoryBenchmark")
            .sourceName("MemoryBenchmark")
            .message("benchmark record #" + i)
            .build();
    }

    private static LogRecord sampleRecordLarge(int i){
        LogRecord.Builder builder = LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("MemoryBenchmark")
            .sourceName("MemoryBenchmark")
            .message("benchmark record with larger structured payload #" + i);
        String valueBase = "x".repeat(LARGE_FIELD_VALUE_LENGTH);
        for(int f = 0; f < LARGE_FIELD_COUNT; f++)
            builder.field("field" + f, valueBase + "-" + f);
        return builder.build();
    }

    /** A {@link LogOutput} that discards every record, only counting them, for measuring pure dispatch cost. **/
    private static final class NoOpLogOutput implements LogOutput{
        private final java.util.concurrent.atomic.LongAdder counter = new java.util.concurrent.atomic.LongAdder();
        private volatile boolean closed = false;

        @Override
        public void publish(LogRecord record){
            if(closed) return;
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
