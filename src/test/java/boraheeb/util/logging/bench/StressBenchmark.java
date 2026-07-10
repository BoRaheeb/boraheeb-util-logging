package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.AsyncLogOutput;
import boraheeb.util.logging.FileLogOutput;
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.Logger;
import boraheeb.util.logging.MemoryLogOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
// -- StressBenchmark Class: ----------------------------------------------
/**
* Standalone stress scenario: multiple threads logging concurrently through a
* single {@link Logger} with two outputs attached simultaneously — an
* {@link AsyncLogOutput} (overflow policy {@code DROP_OLDEST}) wrapping a
* {@link FileLogOutput}, and a {@link MemoryLogOutput} flight recorder — at
* maximum producer speed for a fixed duration.
*
* <p>
*   This mirrors a real degraded-downstream production incident: the file
*   output is intentionally slow relative to producer speed, so the async
*   queue backs up and the overflow policy is exercised under real contention.
* </p>
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.StressBenchmark}</p>
**/
public final class StressBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int PRODUCER_THREADS = 12;
    private static final long DURATION_SECONDS = 6;
    private static final int ASYNC_QUEUE_CAPACITY = 2048;
    private static final int MEMORY_CAPACITY = 4096;
    // -- Constructors: -----------------------------------------------------
    private StressBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws Exception{
        BenchSupport.printHeader("StressBenchmark");
        BenchSupport.metric("Producer threads", PRODUCER_THREADS);
        BenchSupport.metric("Duration", DURATION_SECONDS + " s");

        Path tempDir = Files.createTempDirectory("boraheeb-stress-bench-");
        try{
            runScenario(tempDir);
        }finally{
            deleteRecursively(tempDir);
        }

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenario: -----------------------------------------------------------
    private static void runScenario(Path tempDir) throws Exception{
        Path filePath = tempDir.resolve("stress.log");

        FileLogOutput fileOutput = FileLogOutput.builder()
            .path(filePath)
            .autoFlush(false)
            .minLevel(LogLevel.TRACE)
            .build();

        AsyncLogOutput asyncOutput = AsyncLogOutput.builder(fileOutput)
            .queueCapacity(ASYNC_QUEUE_CAPACITY)
            .overflowPolicy(AsyncLogOutput.OverflowPolicy.DROP_OLDEST)
            .shutdownTimeoutMs(10_000)
            .drainOnClose(true)
            .minLevel(LogLevel.TRACE)
            .build();

        MemoryLogOutput memoryOutput = MemoryLogOutput.builder()
            .capacity(MEMORY_CAPACITY)
            .minLevel(LogLevel.TRACE)
            .build();

        Logger logger = Logger.builder("StressBenchmark")
            .minLevel(LogLevel.TRACE)
            .addOutput(asyncOutput)
            .addOutput(memoryOutput)
            .build();

        AtomicLong attempted = new AtomicLong();
        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(PRODUCER_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(PRODUCER_THREADS);

        for(int t = 0; t < PRODUCER_THREADS; t++){
            final int threadId = t;
            pool.submit(() -> {
                try{
                    startLatch.await();
                    long local = 0;
                    while(!stop.get()){
                        logger.info("stress record thread={} seq={}", threadId, local++);
                        attempted.incrementAndGet();
                    }
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                }finally{
                    doneLatch.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startLatch.countDown();
        Thread.sleep(TimeUnit.SECONDS.toMillis(DURATION_SECONDS));
        stop.set(true);
        doneLatch.await();
        long elapsed = System.nanoTime() - start;
        pool.shutdown();

        // Capture pre-close snapshots: MemoryLogOutput.close() clears its buffer,
        // and AsyncLogOutput's stats are most meaningful before the delegate is torn down.
        int memorySizeBeforeClose = memoryOutput.size();
        AsyncLogOutput.Stats statsBeforeClose = asyncOutput.stats();

        logger.close();

        AsyncLogOutput.Stats statsAfterClose = asyncOutput.stats();
        long fileSize = Files.exists(filePath)? Files.size(filePath) : 0L;

        BenchSupport.printSection("Results");
        BenchSupport.metricThroughput("Attempted log() calls", attempted.get(), elapsed);
        BenchSupport.metric("AsyncLogOutput.Stats (before close)", statsBeforeClose.toString());
        BenchSupport.metric("AsyncLogOutput.Stats (after close)", statsAfterClose.toString());
        BenchSupport.metric("MemoryLogOutput size (before close)", memorySizeBeforeClose);
        BenchSupport.metric("Resulting file size", BenchSupport.formatBytes(fileSize));

        long delivered = statsAfterClose.getDelivered();
        long dropped = statsAfterClose.getDropped();
        long deliveryFailed = statsAfterClose.getDeliveryFailed();
        long accepted = statsAfterClose.getAccepted();
        BenchSupport.metric(
            "Accounting sanity (accepted ~ delivered+dropped+deliveryFailed+pending)",
            "accepted=" + accepted + " delivered=" + delivered + " dropped=" + dropped +
            " deliveryFailed=" + deliveryFailed + " pending=" + statsAfterClose.getPending()
        );
    }
    // -- Private Helper Methods: -------------------------------------------
    private static void deleteRecursively(Path root){
        try{
            if(!Files.exists(root)) return;
            try(var walk = Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try{
                        Files.deleteIfExists(p);
                    }catch(IOException ignore){}
                });
            }
        }catch(IOException ignore){}
    }
}
