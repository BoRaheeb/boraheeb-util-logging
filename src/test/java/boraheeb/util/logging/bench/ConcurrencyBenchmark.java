package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LogOutput;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;
// -- ConcurrencyBenchmark Class: -----------------------------------------
/**
* Standalone benchmark measuring {@link Logger} + {@link LogOutput} aggregate
* throughput under concurrent multi-threaded logging to a single shared
* output, and verifying zero record loss through the in-process dispatch path
* (no file I/O -- isolates contention overhead from I/O overhead).
*
* <p>Not a JUnit test -- run directly via {@code main}.</p>
**/
public final class ConcurrencyBenchmark{

    private ConcurrencyBenchmark(){}

    /** Thread counts exercised, in order. **/
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8, 16};
    /** Records each thread publishes per scenario. **/
    private static final int RECORDS_PER_THREAD = 100_000;
    /** Records each thread publishes during the warmup pass. **/
    private static final int WARMUP_RECORDS_PER_THREAD = 2_000;

    public static void main(String[] args){
        BenchSupport.printHeader("ConcurrencyBenchmark: Logger dispatch throughput under contention");
        BenchSupport.metric("Records per thread", RECORDS_PER_THREAD);

        // Warmup: run a small mixed-thread-count pass first so JIT compiles the
        // Logger/CountingLogOutput hot paths before any timed scenario.
        runScenario(Math.max(2, Runtime.getRuntime().availableProcessors()), WARMUP_RECORDS_PER_THREAD, false);

        boolean allPassed = true;
        for(int threads : THREAD_COUNTS)
            allPassed &= runScenario(threads, RECORDS_PER_THREAD, true);

        BenchSupport.printSection("Zero-loss verification summary");
        BenchSupport.metric("All thread counts verified zero loss", String.valueOf(allPassed));
    }

    /**
    * Runs one scenario: {@code threads} threads each publish {@code recordsPerThread}
    * records concurrently to a single shared {@link CountingLogOutput} through a
    * shared {@link Logger}, then verifies the output counted exactly
    * {@code threads * recordsPerThread} records.
    *
    * @param threads number of concurrent producer threads
    * @param recordsPerThread records published by each thread
    * @param report whether to print metrics for this run (false for the warmup pass)
    * @return {@code true} if the zero-loss check passed
    **/
    private static boolean runScenario(int threads, int recordsPerThread, boolean report){
        CountingLogOutput output = new CountingLogOutput();
        try(Logger logger = Logger.builder("ConcurrencyBenchmark")
                .addOutput(output)
                .build()){

            Thread[] workers = new Thread[threads];
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(threads);

            for(int t = 0; t < threads; t++){
                final int threadIndex = t;
                workers[t] = new Thread(() -> {
                    try{
                        startGate.await();
                        for(int i = 0; i < recordsPerThread; i++)
                            logger.info("thread {} record {}", threadIndex, i);
                    }catch(InterruptedException ex){
                        Thread.currentThread().interrupt();
                    }finally{
                        doneGate.countDown();
                    }
                }, "concurrency-bench-" + t);
                workers[t].setDaemon(true);
            }

            long start = System.nanoTime();
            for(Thread worker : workers) worker.start();
            startGate.countDown();
            try{
                doneGate.await();
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }
            long elapsed = System.nanoTime() - start;

            long expected = (long) threads * (long) recordsPerThread;
            long actual = output.count();

            if(report){
                BenchSupport.printSection(threads + " thread" + (threads == 1? "" : "s"));
                BenchSupport.metricThroughput("Aggregate throughput", expected, elapsed);
                BenchSupport.metric("Expected records", expected);
                BenchSupport.metric("Counted records", actual);
                BenchSupport.metric("Zero-loss check", (actual == expected)? "PASSED" : "FAILED");
            }

            if(actual != expected)
                throw new IllegalStateException(
                    "ConcurrencyBenchmark: record loss detected at " + threads + " threads -> expected " +
                    expected + " but output counted " + actual + " (missing " + (expected - actual) +
                    ") -- this indicates a possible concurrency bug in Logger/LogOutput dispatch"
                );

            return true;
        }
    }

    /** A {@link LogOutput} that only counts published records, isolating dispatch contention from I/O cost. **/
    private static final class CountingLogOutput implements LogOutput{
        private final LongAdder counter = new LongAdder();
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
